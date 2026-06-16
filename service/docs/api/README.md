# Documentation for bulk-inbound-service

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

| Class | Method | HTTP request | Description |
|------------ | ------------- | ------------- | -------------|
| *BatchManagementApi* | [**closeBatch**](Apis/BatchManagementApi.md#closeBatch) | **POST** /batch/fhir/bundle/{id}/$close | Close an existing batch |
*BatchManagementApi* | [**startBatch**](Apis/BatchManagementApi.md#startBatch) | **POST** /batch/fhir/bundle | Start a new batch |
| *BatchUploadApi* | [**processBatchRequest**](Apis/BatchUploadApi.md#processBatchRequest) | **POST** /batch/upload/{id} | Upload documents to a batch |


<a name="documentation-for-models"></a>
## Documentation for Models



<a name="documentation-for-authorization"></a>
## Documentation for Authorization

All endpoints do not require authorization.
