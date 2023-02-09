#!/bin/bash

version=$1
user=$2
password=$3

if [ -z ${version} ] || [ -z ${user} ] || [ -z ${password} ]; then
	echo "Usage: $0 <version> <nexusUser> <nexusPassword>"
	exit 1
fi

baseDirectory=dist/maven2/restlet-${version}/org/restlet/jse
if ! [ -d ${baseDirectory} ]; then
	echo "${baseDirectory} does not exist. Did you specify the correct version?"
	exit 1
fi

for i in `ls ${baseDirectory}`; do
	fileBase=${baseDirectory}/${i}/${version}/${i}-${version}
	echo "uploading ${fileBase}.*"

	if [ -f "${fileBase}.jar" ]; then
		curl -u${user}:${password} -XPOST 'http://nexus.synedra.lan:8081/service/rest/v1/components?repository=thirdparty' \
		-Fmaven2.generate-pom=false \
		-Fmaven2.asset1=@${fileBase}.pom \
		-Fmaven2.asset1.extension=pom \
		-Fmaven2.asset2=@${fileBase}.jar \
		-Fmaven2.asset2.extension=jar \
		-Fmaven2.asset3=@${fileBase}-sources.jar \
		-Fmaven2.asset3.extension=jar \
		-Fmaven2.asset3.classifier=sources
	else
		curl -u${user}:${password} -XPOST 'http://nexus.synedra.lan:8081/service/rest/v1/components?repository=thirdparty' \
		-Fmaven2.generate-pom=false \
		-Fmaven2.asset1=@${fileBase}.pom \
		-Fmaven2.asset1.extension=pom
	fi
done
