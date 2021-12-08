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
					echo 'teste'
					sh 'pwd && ls -lha'
					sh 'cd /tmp/workspace/pipeline-spring/ && ls -lha'
					
					pomFilePath = "/tmp/workspace/pipeline-spring/pom.xml"
					
					
					
				}
			}
		}

		stage ('build') {
			steps {
				script {
					sh sh "mvn clean install -DskipTests=true -P ${pomFilePath} -f ${pomFilePath}"
				}
			}
		}
		stage('Code Analysis') {
			steps {
				script {
					
						def sonarqubeUrl = "http://sonarqube-custom-cicd-tools.apps.cluster-acf0.acf0.sandbox1465.opentlc.com/about"
						def sonarMvnParameters = "-Dsonar.login=4f66a62af0699291699f42a43733328e11f29500 -Dsonar.host.url=${sonarqubeUrl} -Dsonar.scm.provider=git -Dsonar.login=544369dd0e49e2fd3c2e63408fc55b4c887dd6f7 -f ${pomFilePath}"

						sh "mvn sonar:sonar ${sonarMvnParameters}"


				}
			}
		}

	}
}
