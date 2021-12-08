@Library('util')
import com.redhat.Util

def util, artifactId, artifactPath, webhookPayload, appGitBranch, appGitUrl, environment, buildName = '', version = '', projectName = "", appName = "", projectHomePath = "", pomFilePath = ""
def isAppBCExists = false, isAppDCExists = false, hasMavenProfile = false
def isDebugEnabled, isBuildFromFile = false

pipeline {
    agent {
        node { label 'maven' }   
    }
    options {
        timeout(time: 20, unit: 'MINUTES') 
    }
	stages {
		stage ('Print env vars') {
			steps {
				script {
					echo "Environment variables:\n" +
					"\tPROJECT_NAME: ${env.PROJECT_NAME}\n" +
					"\tBUILD_NAME: ${env.BUILD_NAME}\n" +
					"\tAPP_IMG_STREAM: ${env.APP_IMG_STREAM}\n" +
					"\tIS_FROM_FILE: ${env.IS_FROM_FILE}\n" +
					"\tTEMPLATE_NAME: ${env.TEMPLATE_NAME}\n" +
					"\tURL_LIVENESS: ${env.URL_LIVENESS}\n" +
					"\tURL_READINESS: ${env.URL_READINESS}\n" +
					"\tPROJECT_HOME_PATH: ${env.PROJECT_HOME_PATH}\n" +
					"\tMAVEN_PROFILE: ${env.MAVEN_PROFILE}\n" +
					"\tGIT_CREDENTIALS: ${env.GIT_CREDENTIALS}\n"
				}
			}
		}
		stage ('Printing payload info') {
			steps {
				script {
					try {
						echo "Variables from shell: payload ${payload}"
					} catch (MissingPropertyException e) {
						echo "Webhook não configurado corretamente, ou pipeline iniciado manualmente pelo Openshift/Jenkins. Iniciar pipeline com push na branch requerida."
					}
				}
			}
		}
		stage ('Init Pipeline') {
			steps {
				script {
					util = new com.redhat.Util();
					util.enableDebug(true);
					webhookPayload = readJSON text: env.payload
					appGitBranch = webhookPayload.ref
					appGitBranch = appGitBranch.replace("refs/heads/", "")
					appGitUrl = webhookPayload.project.git_http_url

					echo "Branch: ${appGitBranch}"
					echo "Git url: ${appGitUrl}"

					buildName = env.BUILD_NAME
					appName = buildName

					if (appGitBranch.equals("develop")){
						echo "Iniciando pipeline para a branch development"
						environment = 'dev'
						projectName = "${env.PROJECT_NAME}" + '-' + environment
					} else if (appGitBranch ==~ /release.*/ ){
						echo "Iniciando pipeline para a branch ${appGitBranch}"
						environment = 'hml'
						projectName = "${env.PROJECT_NAME}" + '-' + environment
					} else if (appGitBranch ==~ /hotfix.*/ ){
						echo "Iniciando pipeline para a branch ${appGitBranch}"
						environment = 'hotfix'
						projectName = "${env.PROJECT_NAME}-hotfix"
					} else {
						currentBuild.result = 'ABORTED'
						error("Branch não reconhecida: ${appGitBranch}. Finalizando pipeline.")					
					}

					echo "Pipeline inicializado pelo commit realizado:\n " +
						"\tAutor: ${webhookPayload.user_name}\n " +
						"\tBranch: ${appGitBranch}\n" +
						"\tRepositório url: ${appGitUrl}"
					
 					openshift.withCluster() {
 						openshift.withProject(projectName) {
 							isAppBCExists = openshift.selector("bc", buildName).exists()
 							isAppDCExists = openshift.selector("dc", appName).exists()
 						}
 					}

					isBuildFromFile = env.IS_FROM_FILE == 'true'
					echo "Build a partir de artefato? ${isBuildFromFile}"

					if("".equals(env.PROJECT_HOME_PATH)){
						projectHomePath = "."
					} else {
						projectHomePath = env.PROJECT_HOME_PATH
					}

					pomFilePath = "${projectHomePath}/pom.xml"

					if("".equals(env.MAVEN_PROFILE)){
						hasMavenProfile = false
					} else {
						hasMavenProfile = true
					}
				}
			}
		}
		stage ('Ambiente DES'){
			when {
				expression { return "dev".equals(environment) }
			}
			steps{
				script {
					echo "Deploy sera realizado no ambiente de Desenvolvimento."
				}
			}	
		}
		stage ('Ambiente HOM'){
			when {
				expression { return "hml".equals(environment) }
			}
			steps{
				script {
					echo "Deploy sera realizado no ambiente de Homologação."
				}
			}	
		}
		stage ('Ambiente HOTFIX'){
			when {
				expression { return "hotfix".equals(environment) }
			}
			steps{
				script {
					echo "Deploy sera realizado no ambiente de Hotfix."
				}
			}	
		}
		stage ('Build') {
			steps {
				script {
					echo "Clone GIT URL: ${appGitUrl} da branch: ${appGitBranch}"
				}
				git credentialsId: env.GIT_CREDENTIALS, branch: appGitBranch, url: appGitUrl
				script {
					def pom = readMavenPom file: "${pomFilePath}"
					version = pom.version
					artifactId = pom.build.finalName == null ? "${pom.artifactId}-${version}" : pom.build.finalName
					packaging = pom.packaging
					artifactPath = "${projectHomePath}/target/${artifactId}.${packaging}"
					
					if(hasMavenProfile){
						sh "mvn clean install -DskipTests=true -P ${pomFilePath} -f ${pomFilePath}"
					} else {
						sh "mvn clean install -DskipTests=true -f ${pomFilePath}"
					}
				}
			}
		}
		stage('Code Analysis') {
			steps {
				script {
					try{
						def sonarqubeUrl = "https://sonarqube-custom-cicd-tools.apps.ocp.sefaz.ma.gov.br"
						def sonarMvnParameters = "-Dsonar.login=4f66a62af0699291699f42a43733328e11f29500 -Dsonar.host.url=${sonarqubeUrl} -Dsonar.scm.provider=git -Djavax.net.ssl.trustStore=/home/jenkins/.m2/nexus-ca-jks -Djavax.net.ssl.trustStorePassword=$NEXUS_CA_JKS_PWD -f ${pomFilePath}"
						if(hasMavenProfile){
							sh "mvn sonar:sonar -P ${pomFilePath} ${sonarMvnParameters}"
						} else {
							sh "mvn sonar:sonar ${sonarMvnParameters}"
						}
					} catch(Exception e) {
						echo "Erro ao chamar o Sonar. - " + e.getMessage()
					}
				}
			}
		}
		stage('Archive') {
			steps {
				script {
					sh "mvn deploy -DskipTests=true -f ${pomFilePath}"
				}
			}
		}
		stage('Config Files') {
		    steps {
		        script {
		            try {
		                sh "rm -rf service-config"
		            } catch (e) {
		                echo e
		            }
		            sh "mkdir -p service-config"
		            dir('service-config') {
		                sh "git init"
		                withCredentials([usernameColonPassword(credentialsId: 'jenkins-sa', variable: 'jenkinssa')]) {
		                    def split = jenkinssa.split(':')
		                    def credentials = split[0]+":"+java.net.URLEncoder.encode(split[1], "UTF-8")
    		                sh "git remote add -f origin http://${credentials}@git.sefaz.ma.gov.br/pipelines/service-config/"
		                }
		                sh "git config core.sparseCheckout true"
		                sh "git config pull.rebase false"
		                sh "echo '${appName}' >> .git/info/sparse-checkout"
		                sh "git pull origin master"
		                def serviceConfigEnv = appName + '/'
		                if (environment.equals('hotfix'))
		                    serviceConfigEnv = serviceConfigEnv + 'hml/'
		                else
		                    serviceConfigEnv = serviceConfigEnv + environment + '/'
    					def files = findFiles(glob: "${serviceConfigEnv}/*.y*ml")
    					echo("Files: ${files}")
    					for(file in files){
    						def path = file.path
    						def yamlFile = readYaml(file: path)
    						def kind = yamlFile.kind
    						def name = yamlFile.metadata.name
    						util.createOrReplace(projectName, kind, name, path, appName)
    					}
		            }
		        }
		    }
		}
		stage('Build Creation') {
			when {
				expression {
					openshift.withCluster() {
						openshift.withProject(projectName) {
							echo "BuildConfig da aplicação existe? ${isAppBCExists}"
							return !isAppBCExists
						}
					} 
				}
			}
			steps {
				script {
					util.createBuild(
						projectName,
						buildName,
						env.APP_IMG_STREAM,
						artifactPath,
						isBuildFromFile)
				}
			}
		}
		stage('Build Start') {
			steps {
				script {
					util.startBuild(
						projectName
						, buildName
						, artifactPath
						, isBuildFromFile)
				}
			}
		}
		stage('Image tag'){
			when {
				expression { return "hml".equals(environment) || "hotfix".equals(environment) }
			}
			steps{
				script{
					openshift.withCluster(){
						openshift.withProject(projectName){
							openshift.tag(projectName+"/"+appName+":latest", projectName+"/"+appName+":${version}")
						}
					}
				}
			}
		}
		stage('Application Creation') {
			when {
				expression { return !isAppDCExists }
			}
			steps {
				script {
				    templateName = ""
				    if(env.TEMPLATE_NAME?.trim())
				        templateName = env.TEMPLATE_NAME.trim()
					util.newApp(
						projectName
						, environment
						, appName
						, templateName
						, env.URL_READINESS
						, env.URL_LIVENESS
						, 'latest'
						, 'apps.ocp.sefaz.ma.gov.br')
				}
			}
		}
		stage("Waiting for deployment") {
			steps {
				script {
					util.verifyDeployment(projectName, appName)
				}
			}
		}
	}
}
