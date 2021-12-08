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
		stage ('test') {
			steps {
				script {
					sh "mvn test -P ${pomFilePath} -f ${pomFilePath} -DTODO_LIST_EDITABLE=true"
				}
			}
		}
		stage ('build') {
			steps {
				script {
					sh "mvn clean install -DskipTests=true -P ${pomFilePath} -f ${pomFilePath}"
				}
			}
		}
		
	}
}
