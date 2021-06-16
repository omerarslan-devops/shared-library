def projectName="" // test project
def buildType = "" // quarkusAppBuild, libraryBuild
def deploymentType = "" // MavenDeploy
def appName = ""
def chartmuseum = ""
def uiTargetedEnv = ""

def runPipeline(Map parameters) {
    pipeline {
            node {
                label 'main'

                stage("Echo Parameters") {
                  	print parameters
                  	appName = parameters.appName
                  	buildType = parameters.buildType
                  	deploymentType = parameters.deploymentType
                  	projectName = parameters.projectName
            	} 

                stage("Checkout") {
                  	checkout scm
            	} 

                stage("Build") {
                    buildType = getBuildType()
                    echo "BUILD TYPE:"
                    echo buildType
                    if (buildType == "npm") {
                    	npmBuild()
                    } else if (buildType == "maven") {
                    	mavenBuild()
                    } else if (buildType == "MavenDeploy") {
                    	mavenDeploy()
                    } else if (buildType == "LibDeploy") {
                    	libDeploy()
                    }  else if (buildType == "S2I") {
                    	echo "NO BUILD, DIRECT DOCKER BUILD"
                    } else if (buildType == "quarkus") {
                    	quarkusBuild()
                    } 
            	} 

            	stage ("Dockerize") {
            		echo "Docker"
            		if (deploymentType == "KieServer" || buildType == "LibDeploy") {
            			echo "NO DOCKER BUILD"
            		} else {
            			buildAndPushDockerImage()
            		}
            		
            	}

            	stage ("Deploy") {
            		echo "Deploy"
					
            		if (deploymentType == "KieServer" || buildType == "LibDeploy") {
            			//deployToKieServer()
						echo "NO DEPLOYMENT"
            		} else {
            			triggerDeploymentToOpenshift()
            		}
            		
            	}
            }
        }
} 
return this