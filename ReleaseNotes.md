<div style="text-align:right"><img src="https://raw.githubusercontent.com/gematik/gematik.github.io/master/Gematik_Logo_Flag_With_Background.png" width="250" height="47" alt="gematik GmbH Logo"/> <br/> </div> <br/>

# Release notes
## Release 1.0.0
- added automatic API doc generation
- Updated base-image and updated from java 21 to java 25
- Removed istio helm chart
- Receives a batch request and writes it to the message broker
- Format of messages for batch upload to broker has been specified
- Handling of error situations
- JWT-validation activated
- Copying from in queue to secure queue
- Endpoint to create a batch
- Batch permission check
- Sending messages to WAF service
- Endpoint for closing a batch
- Sending close batch message to WAF service
- Removed queue configs
- Counting messages in a batch
- On close request the batch will be marked as closed in the database
- Batch upload only supports content type application/fhir+ndjson
- Adapted to new HTTP headers for routing 
- message encryption
- Upgraded to spring boot 4
- Added secret mapping for seperate rabbitmq user, password and vhost
- Added retry mechanism to message listener for sending messages to WAF service
- Added application information for logging to application.yaml
- Added observation for rabbit listener
- Added purger module to delete old batches from database