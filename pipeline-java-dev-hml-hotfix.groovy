def appGitBranch, appGitUrl,pomFilePath = "",projectHomePath = ""

pipeline {
    agent {
        node { label 'maven' }   
    }
    options {
        timeout(time: 20, unit: 'MINUTES') 
    }
	stages {
		stage ('init pipeline') {
			steps {
				script {
					appGitBranch='main'
					appGitUrl='https://github.com/lennondsantos/todo-list-spring-boot.git'
				}
			}
		}
		stage ('clone') {
			steps {
				script {
					git branch: appGitBranch, url: appGitUrl
					
					pomFilePath = "/tmp/workspace/pipeline-spring/pom.xml"	
				}
			}
		}
		/*
		stage ('test') {
			steps {
				script {
					sh "mvn test -P ${pomFilePath} -f ${pomFilePath} -DTODO_LIST_EDITABLE=true"
				}
			}
		}
		*/
		stage ('build') {
			steps {
				script {
					sh "mvn clean install -DskipTests=true -P ${pomFilePath} -f ${pomFilePath}"
				}
			}
		}
		stage('Code Analysis') {
			steps {
				script {
					try{
						def sonarqubeUrl = "http://sonarqube-custom-cicd-tools.apps.cluster-acf0.acf0.sandbox1465.opentlc.com"
						//def sonarMvnParameters = "-Dsonar.login=544369dd0e49e2fd3c2e63408fc55b4c887dd6f7 -Dsonar.host.url=${sonarqubeUrl} -Dsonar.scm.provider=git  -f ${pomFilePath}"
						def sonarMvnParameters = "-Dsonar.host.url=${sonarqubeUrl} -Dsonar.scm.provider=git  -f ${pomFilePath}"

						sh "mvn sonar:sonar ${sonarMvnParameters}"

					} catch(Exception e) {
						echo "Erro ao chamar o Sonar. - " + e.getMessage()
					}
				}
			}
		}
		stage('Apply role to Jenkins user') {
			steps {
				script {					
					sh "oc policy add-role-to-user edit system:serviceaccount:cicd-tools:jenkins --rolebinding-name=jenkins-edit -n foo-dev"
				}
			}
		}
		stage('Create Empty Image with java') {
			when{
				expression{return !openshift.selector('bc/todo-list-backend').exists()}	  
			}
			steps {
				script {					
					sh "oc new-build --name=todo-list-backend --image-stream=java:openjdk-11-ubi8 --binary -n foo-dev"
				}
			}
		}
		stage('Start build Image') {
			steps {
				script {	
					sh "oc start-build todo-list-backend --from-file=target/todo-list-backend-0.0.1-SNAPSHOT.jar -w -F -n foo-dev"
				}
			}
		}	
		stage('Deploy Image created') {
			when{
				expression{return !openshift.selector('dc/todo-list-backend').exists()}		  
			}
			steps {
				script {
					sh "oc new-app --name=todo-list-backend --image-stream=foo-dev/todo-list-backend:latest -n foo-dev"
				}
			}
		}
		stage('Apply configs') {
			steps {
				script {
					appGitBranch='dev'
					appGitUrl='https://github.com/lennondsantos/repo-config.git'
					
					git branch: appGitBranch, url: appGitUrl
					
					openshift.withCluster(){
						openshift.withProject('foo-dev'){
							//if(openshift.selector('configmap/todo-list-spring-boot').exists()){
							//
							//}
							def files = findFiles(glob: "todo-list-spring-boot/config-map/*.y*ml")
							for(file in files){
								openshift.apply(file)
								//def path = file.path
								//def yamlFile = readYaml(file: path)
							}
							
						}
					}
				}
			}
		}
	}
}
