/*
 * Copyright 2005-2008 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package org.restlet.ext.wadl;

import java.util.List;

import org.restlet.data.Method;

/**
 * Describes the expected requests and responses of a resource method.
 * 
 * @author Jerome Louvel
 */
public class MethodInfo {

    private List<DocumentationInfo> documentations;

    private String identifier;

    private Method name;

    private RequestInfo request;

    private ResponseInfo response;

    public List<DocumentationInfo> getDocumentations() {
        return documentations;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Method getName() {
        return name;
    }

    public RequestInfo getRequest() {
        return request;
    }

    public ResponseInfo getResponse() {
        return response;
    }

    public void setDocumentations(List<DocumentationInfo> doc) {
        this.documentations = doc;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public void setName(Method name) {
        this.name = name;
    }

    public void setRequest(RequestInfo request) {
        this.request = request;
    }

    public void setResponse(ResponseInfo response) {
        this.response = response;
    }

}
