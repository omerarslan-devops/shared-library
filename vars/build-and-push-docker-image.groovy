def buildAndPushDockerImage(imageName, imageEnvironment, imageVersion) {
	echo "${env.JOB_NAME}"
    echo "${env.BRANCH_NAME}"
    echo "${env.BUILD_ID}"
	imageDefinition = imageName + "-" + imageEnvironment + ":" + imageVersion
	echo imageDefinition
    docker.withRegistry('https://aefes.azurecr.io', 'azure-container-registry') {
        def dockerImage = docker.build("bpm-aefes-hierarchy-qa:${env.BUILD_ID}")
		dockerImage.push()
	}
}

def exampleMethod(name) {
    println("name:" + name)
}
return this