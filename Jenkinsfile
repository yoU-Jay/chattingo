// Jenkinsfile

// Load the pipeline from your shared-libraries folder
def chattingoPipeline = load 'shared-libraries/vars/chattingoPipeline.groovy'

// Call the pipeline (optional: you can pass config overrides)
chattingoPipeline(
    dockerhubCreds: 'dockerhub-creds',   // optional, defaults to same value in library
    deployDir: '/opt/chattingo_env'      // optional, defaults to same value in library
)
