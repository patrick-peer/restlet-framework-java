package org.restlet.ext.apispark.internal.conversion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.restlet.Server;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Protocol;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.engine.Engine;
import org.restlet.engine.connector.ConnectorHelper;
import org.restlet.ext.apispark.internal.info.ApplicationInfo;
import org.restlet.ext.apispark.internal.info.MethodInfo;
import org.restlet.ext.apispark.internal.info.ParameterInfo;
import org.restlet.ext.apispark.internal.info.ParameterStyle;
import org.restlet.ext.apispark.internal.info.PropertyInfo;
import org.restlet.ext.apispark.internal.info.RepresentationInfo;
import org.restlet.ext.apispark.internal.info.ResourceInfo;
import org.restlet.ext.apispark.internal.info.ResponseInfo;
import org.restlet.ext.apispark.internal.model.Contract;
import org.restlet.ext.apispark.internal.model.Definition;
import org.restlet.ext.apispark.internal.model.Endpoint;
import org.restlet.ext.apispark.internal.model.Header;
import org.restlet.ext.apispark.internal.model.Operation;
import org.restlet.ext.apispark.internal.model.PathVariable;
import org.restlet.ext.apispark.internal.model.PayLoad;
import org.restlet.ext.apispark.internal.model.Property;
import org.restlet.ext.apispark.internal.model.QueryParameter;
import org.restlet.ext.apispark.internal.model.Representation;
import org.restlet.ext.apispark.internal.model.Resource;
import org.restlet.ext.apispark.internal.model.Response;
import org.restlet.ext.apispark.internal.reflect.ReflectUtils;
import org.restlet.ext.apispark.internal.utils.IntrospectionUtils;

/**
 * Tools library for converting the model used for introspection to Restlet Web
 * API Definition.
 * 
 * @author Cyprien Quilici
 */
public class DefinitionTranslator {

    /** Internal logger. */
    protected static Logger LOGGER = Logger.getLogger(DefinitionTranslator.class
            .getName());
    
    /**
     * Completes a map of representations with a list of representations.
     * 
     * @param mapReps
     *            The map to complete.
     * @param representations
     *            The source list.
     */
    private static void addRepresentations(
            Map<String, RepresentationInfo> mapReps,
            List<RepresentationInfo> representations) {
        if (representations != null) {
            for (RepresentationInfo r : representations) {
                if (!mapReps.containsKey(r.getIdentifier())) {
                    mapReps.put(r.getIdentifier(), r);
                }
            }
        }
    }

    /**
     * Completes the given {@link Contract} with the list of resources.
     * 
     * @param application
     *            The source application.
     * @param contract
     *            The contract to complete.
     * @param resources
     *            The list of resources.
     * @param basePath
     *            The resources base path.
     * @param mapReps
     *            The lndex of representations.
     */
    private static void addResources(ApplicationInfo application,
            Contract contract, List<ResourceInfo> resources, String basePath,
            Map<String, RepresentationInfo> mapReps) {
        // TODO add section sorting strategies
        for (ResourceInfo ri : resources) {
            Resource resource = new Resource();
            resource.setDescription(ri.getDescription());
            resource.setName(ri.getName());
            resource.setAuthenticationProtocol(ri.getAuthenticationProtocol());
            if (ri.getPath() == null) {
                resource.setResourcePath("/");
            } else if (!ri.getPath().startsWith("/")) {
                resource.setResourcePath("/" + ri.getPath());
            } else {
                resource.setResourcePath(ri.getPath());
            }

            resource.setPathVariables(new ArrayList<PathVariable>());
            for (ParameterInfo pi : ri.getParameters()) {
                if (ParameterStyle.TEMPLATE.equals(pi.getStyle())) {
                    PathVariable pathVariable = new PathVariable();

                    pathVariable.setDescription(pi.getDescription());
                    pathVariable.setName(pi.getName());

                    resource.getPathVariables().add(pathVariable);
                }
            }

            if (!ri.getChildResources().isEmpty()) {
                addResources(application, contract, ri.getChildResources(),
                        resource.getResourcePath(), mapReps);
            }
            LOGGER.fine("Resource " + ri.getPath() + " added.");

            if (ri.getMethods().isEmpty()) {
                LOGGER.warning("Resource " + ri.getName() + " has no methods.");
                continue;
            }

            resource.setOperations(new ArrayList<Operation>());
            for (MethodInfo mi : ri.getMethods()) {
                String methodName = mi.getMethod().getName();
                if ("OPTIONS".equals(methodName) || "PATCH".equals(methodName)) {
                    LOGGER.fine("Method " + methodName + " ignored.");
                    continue;
                }
                LOGGER.fine("Method " + methodName + " added.");
                Operation operation = new Operation();
                operation.setDescription(mi.getDescription());
                if (mi.getName() != null && !"".equals(mi.getName())) {
                    operation.setName(mi.getName());
                } else {
                    operation.setName(mi.getAnnotation().getJavaMethod()
                            .getName());
                }
                operation.setMethod(mi.getMethod().getName());

                // Fill fields produces/consumes
                String mediaType;
                if (mi.getRequest() != null
                        && mi.getRequest().getRepresentations() != null) {
                    List<RepresentationInfo> consumed = mi.getRequest()
                            .getRepresentations();
                    for (RepresentationInfo reprInfo : consumed) {
                        mediaType = reprInfo.getMediaType().getName();
                        operation.getConsumes().add(mediaType);
                    }
                }

                if (mi.getResponse() != null
                        && mi.getResponse().getRepresentations() != null) {
                    List<RepresentationInfo> produced = mi.getResponse()
                            .getRepresentations();
                    for (RepresentationInfo reprInfo : produced) {
                        mediaType = reprInfo.getMediaType().getName();
                        operation.getProduces().add(mediaType);
                    }
                }

                // Complete parameters
                operation.setHeaders(new ArrayList<Header>());
                operation.setQueryParameters(new ArrayList<QueryParameter>());
                if (mi.getRequest() != null) {
                    for (ParameterInfo pi : mi.getRequest().getParameters()) {
                        if (ParameterStyle.HEADER.equals(pi.getStyle())) {
                            Header header = new Header();
                            header.setAllowMultiple(pi.isRepeating());
                            header.setDefaultValue(pi.getDefaultValue());
                            header.setDescription(toString(pi.getDescription(),
                                    pi.getDefaultValue()));
                            header.setName(pi.getName());
                            header.setEnumeration(new ArrayList<String>());
                            header.setRequired(pi.isRequired());

                            operation.getHeaders().add(header);
                        } else if (ParameterStyle.QUERY.equals(pi.getStyle())) {
                            QueryParameter queryParameter = new QueryParameter();
                            queryParameter.setAllowMultiple(pi.isRepeating());
                            queryParameter
                                    .setDefaultValue(pi.getDefaultValue());
                            queryParameter.setDescription(toString(
                                    pi.getDescription(), pi.getDefaultValue()));
                            queryParameter.setName(pi.getName());
                            queryParameter
                                    .setEnumeration(new ArrayList<String>());
                            queryParameter.setRequired(pi.isRequired());

                            operation.getQueryParameters().add(queryParameter);
                        }
                    }
                }
                for (ParameterInfo pi : mi.getParameters()) {
                    if (ParameterStyle.HEADER.equals(pi.getStyle())) {
                        Header header = new Header();
                        header.setAllowMultiple(pi.isRepeating());
                        header.setDefaultValue(pi.getDefaultValue());
                        header.setDescription(toString(pi.getDescription(),
                                pi.getDefaultValue()));
                        header.setName(pi.getName());
                        header.setEnumeration(new ArrayList<String>());
                        header.setRequired(pi.isRequired());

                        operation.getHeaders().add(header);
                    } else if (ParameterStyle.QUERY.equals(pi.getStyle())) {
                        QueryParameter queryParameter = new QueryParameter();
                        queryParameter.setAllowMultiple(pi.isRepeating());
                        queryParameter.setDefaultValue(pi.getDefaultValue());
                        queryParameter.setDescription(toString(
                                pi.getDescription(), pi.getDefaultValue()));
                        queryParameter.setName(pi.getName());
                        queryParameter.setEnumeration(new ArrayList<String>());
                        queryParameter.setRequired(pi.isRequired());

                        operation.getQueryParameters().add(queryParameter);
                    }
                }

                if (mi.getRequest() != null
                        && mi.getRequest().getRepresentations() != null
                        && !mi.getRequest().getRepresentations().isEmpty()) {
                    addRepresentations(mapReps, mi.getRequest()
                            .getRepresentations());

                    PayLoad entity = new PayLoad();
                    // TODO analyze
                    // The models differ : one representation / one variant
                    // for Restlet one representation / several variants for
                    // APIspark
                    entity.setType(mi.getRequest().getRepresentations().get(0)
                            .getType().getSimpleName());
                    entity.setArray(mi.getRequest().getRepresentations().get(0)
                            .isCollection());

                    operation.setInputPayLoad(entity);
                }

                if (mi.getResponses() != null && !mi.getResponses().isEmpty()) {
                    operation.setResponses(new ArrayList<Response>());

                    PayLoad entity = new PayLoad();
                    // TODO analyze
                    // The models differ : one representation / one variant
                    // for Restlet one representation / several variants for
                    // APIspark
                    if (!mi.getResponse().getRepresentations().isEmpty()) {
                        entity.setType(mi.getResponse().getRepresentations()
                                .get(0).getType().getSimpleName());
                        entity.setArray(mi.getResponse().getRepresentations()
                                .get(0).isCollection());
                    }

                    for (ResponseInfo rio : mi.getResponses()) {
                        addRepresentations(mapReps, rio.getRepresentations());

                        if (!rio.getStatuses().isEmpty()) {
                            Status status = rio.getStatuses().get(0);
                            // TODO analyze
                            // The models differ : one representation / one
                            // variant
                            // for Restlet one representation / several
                            // variants for
                            // APIspark

                            Response response = new Response();
                            response.setOutputPayLoad(entity);
                            response.setCode(status.getCode());
                            response.setName(toString(rio.getDescription()));
                            response.setDescription(toString(rio
                                    .getDescription()));
                            response.setMessage(status.getDescription());
                            // response.setName();

                            operation.getResponses().add(response);
                        }
                    }
                }

                resource.getOperations().add(operation);
            }

            // TODO assign sections to resource
            // resource.getSections().add(section.getName());
            contract.getResources().add(resource);
        }
    }

    private static String convertPrimitiveTypes(String type) {
        if ("int".equals(type)) {
            return "Integer";
        } else if ("boolean".equals(type)) {
            return "Boolean";
        } else if ("long".equals(type)) {
            return "Long";
        } else if ("float".equals(type)) {
            return "Float";
        } else if ("double".equals(type)) {
            return "Double";
        } else {
            return type;
        }
    }

    private static boolean isPrimitiveType(String type) {
        String[] primitiveTypes = { "int", "Integer", "boolean", "Boolean",
                "double", "Double", "float", "Float", "long", "Long" };
        List<String> smartPrimitiveTypes = Arrays.asList(primitiveTypes);
        return smartPrimitiveTypes.contains(type);
    }

    /**
     * Translates a ApplicationInfo to a {@link Definition} object.
     * 
     * @param application
     *            The {@link ApplicationInfo} instance.
     * @return The definintion instance.
     */
    public static Definition toDefinition(ApplicationInfo application) {
        Definition result = null;
        if (application != null) {
            result = new Definition();
            result.setVersion(application.getVersion());
            Reference ref = application.getResources().getBaseRef();
            if (ref != null) {
                result.getEndpoints().add(
                        new Endpoint(ref.getHostDomain(), ref.getHostPort(),
                                ref.getSchemeProtocol().getSchemeName(), ref
                                        .getPath(), null));
            } else {
                result.getEndpoints().add(
                        new Endpoint("example.com", 80, Protocol.HTTP
                                .getSchemeName(), "/v1",
                                ChallengeScheme.HTTP_BASIC.getName()));
            }

            Contract contract = new Contract();
            result.setContract(contract);
            contract.setDescription(toString(application.getDescription()));
            contract.setName(application.getName());
            if (contract.getName() == null || contract.getName().isEmpty()) {
                contract.setName(application.getClass().getName());
                LOGGER.log(Level.WARNING,
                        "Please provide a name to your application, used "
                                + contract.getName() + " by default.");
            }
            LOGGER.fine("Contract " + contract.getName() + " added.");

            // List of resources.
            Map<String, RepresentationInfo> mapReps = new HashMap<String, RepresentationInfo>();
            addResources(application, contract, application.getResources()
                    .getResources(), result.getEndpoints().get(0).computeUrl(),
                    mapReps);

            java.util.List<String> protocols = new ArrayList<String>();
            for (ConnectorHelper<Server> helper : Engine.getInstance()
                    .getRegisteredServers()) {
                for (Protocol protocol : helper.getProtocols()) {
                    if (!protocols.contains(protocol.getName())) {
                        LOGGER.fine("Protocol " + protocol.getName()
                                + " added.");
                        protocols.add(protocol.getName());
                    }
                }
            }

            // List of representations.
            for (RepresentationInfo ri : application.getRepresentations()) {
                if (!mapReps.containsKey(ri.getIdentifier())) {
                    mapReps.put(ri.getIdentifier(), ri);
                }
            }
            // This first phase discovers representations related to annotations
            // Let's cope with the inheritance chain, and complex properties
            List<RepresentationInfo> toBeAdded = new ArrayList<RepresentationInfo>();
            // Initialize the list of classes to be anaylized
            for (RepresentationInfo ri : mapReps.values()) {
                if (ri.isRaw()) {
                    continue;
                }
                if (ri.isCollection()
                        && !mapReps.containsKey(ri.getType().getName())) {
                    // Check if the type has been described.
                    RepresentationInfo r = new RepresentationInfo(
                            ri.getMediaType());
                    r.setType(ri.getType());
                    toBeAdded.add(r);
                }
                // Parent class
                Class<?> parentType = ri.getType().getSuperclass();
                if (parentType != null && ReflectUtils.isJdkClass(parentType)) {
                    // TODO This type must introspected too, as it will reveal
                    // other representation
                    parentType = null;
                }
                if (parentType != null
                        && !mapReps.containsKey(parentType.getName())) {
                    RepresentationInfo r = new RepresentationInfo(
                            ri.getMediaType());
                    r.setType(parentType);
                    toBeAdded.add(r);
                }
                for (PropertyInfo pi : ri.getProperties()) {
                    if (pi.getType() != null
                            && !mapReps.containsKey(pi.getType().getName())
                            && !toBeAdded.contains(pi.getType())) {
                        RepresentationInfo r = new RepresentationInfo(
                                ri.getMediaType());
                        r.setType(pi.getType());
                        toBeAdded.add(r);
                    }
                }
            }
            // Second phase, discover classes and loop while classes are unknown
            while (!toBeAdded.isEmpty()) {
                RepresentationInfo[] tab = new RepresentationInfo[toBeAdded
                        .size()];
                toBeAdded.toArray(tab);
                toBeAdded.clear();
                for (int i = 0; i < tab.length; i++) {
                    RepresentationInfo current = tab[i];
                    if (!current.isRaw()
                            && !ReflectUtils.isJdkClass(current.getType())) {
                        if (!mapReps.containsKey(current.getName())) {
                            // TODO clearly something is wrong here. We should
                            // list all representations when discovering the
                            // method.
                            RepresentationInfo ri = RepresentationInfo
                                    .introspect(current.getType(), null,
                                            current.getMediaType());
                            mapReps.put(ri.getIdentifier(), ri);
                            // have a look at the parent type

                            Class<?> parentType = ri.getType().getSuperclass();
                            if (parentType != null
                                    && ReflectUtils.isJdkClass(parentType)) {
                                // TODO This type must introspected too, as it
                                // will reveal
                                // other representation
                                parentType = null;
                            }
                            if (parentType != null
                                    && !mapReps.containsKey(parentType
                                            .getName())) {
                                RepresentationInfo r = new RepresentationInfo(
                                        ri.getMediaType());
                                r.setType(parentType);
                                toBeAdded.add(r);
                            }
                            for (PropertyInfo prop : ri.getProperties()) {
                                if (prop.getType() != null
                                        && !mapReps.containsKey(prop.getType()
                                                .getName())
                                        && !toBeAdded.contains(prop.getType())) {
                                    RepresentationInfo r = new RepresentationInfo(
                                            ri.getMediaType());
                                    r.setType(prop.getType());
                                    toBeAdded.add(r);
                                }
                            }
                        }
                    }
                }
            }

            // TODO add section sorting strategies
            for (RepresentationInfo ri : mapReps.values()) {
                if (ri.isCollection()) {
                    continue;
                }
                LOGGER.fine("Representation " + ri.getName() + " added.");
                Representation representation = new Representation();

                // TODO analyze
                // The models differ : one representation / one variant for
                // Restlet
                // one representation / several variants for APIspark
                representation.setDescription(toString(ri.getDescription()));
                representation.setName(ri.getName());

                representation.setProperties(new ArrayList<Property>());
                for (PropertyInfo pi : ri.getProperties()) {
                    LOGGER.fine("Property " + pi.getName() + " added.");
                    Property p = new Property();
                    p.setDefaultValue(pi.getDefaultValue());
                    p.setDescription(pi.getDescription());
                    p.setMax(pi.getMax());
                    p.setMaxOccurs(pi.getMaxOccurs());
                    p.setMin(pi.getMin());
                    p.setMinOccurs(pi.getMinOccurs());
                    p.setName(pi.getName());
                    p.setEnumeration(pi.getEnumeration());
                    if (pi.getType() != null) {
                        // TODO: handle primitive type, etc
                        String type = pi.getType().getSimpleName();
                        if (isPrimitiveType(type)) {
                            p.setType(convertPrimitiveTypes(pi.getType()
                                    .getSimpleName()));
                        } else {
                            p.setType(type);
                        }
                    }

                    p.setUniqueItems(pi.isUniqueItems());

                    representation.getProperties().add(p);
                }

                representation.setRaw(ri.isRaw()
                        || ReflectUtils.isJdkClass(ri.getType()));
                // TODO add representation's sections
                // representation.getSections().add(section.getName());
                contract.getRepresentations().add(representation);
            }
        }
        IntrospectionUtils.sortDefinition(result);
        return result;
    }

    /**
     * Returns the description if not empty, otherwise an empty string
     *
     * @return A String value.
     */
    private static String toString(String description) {
        return toString(description, "");
    }

    /**
     * Returns the description if not empty, otherwise the default value
     *
     * @return A String value.
     */
    private static String toString(String description, String defaultValue) {
        if (description != null && !description.isEmpty()) {
            return description;
        }

        return defaultValue;
    }

    /**
     * Private constructor to ensure that the class acts as a true utility class
     * i.e. it isn't instantiable and extensible.
     */
    private DefinitionTranslator() {
    }

}