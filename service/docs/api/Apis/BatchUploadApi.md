# BatchUploadApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**processBatchRequest**](BatchUploadApi.md#processBatchRequest) | **POST** /batch/upload/{id} | Upload documents to a batch |


<a name="processBatchRequest"></a>
# **processBatchRequest**
> processBatchRequest(id, x-sender, Authorization, X-Document-Ids, body)

Upload documents to a batch

    Uploads one or more FHIR documents into the batch identified by the given id. The request body must be a newline-delimited stream of FHIR resources serialized as application/fhir+ndjson (one resource per line). The uploaded documents are processed asynchronously. All error responses are returned as a FHIR OperationOutcome.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **id** | **UUID**| Unique identifier of the target batch. | [default to null] |
| **x-sender** | **String**| Identifier of the sender uploading the documents. | [default to null] |
| **Authorization** | **String**| Bearer token authorizing the upload. | [default to null] |
| **X-Document-Ids** | **String**| Comma-separated list of document identifiers contained in the upload. | [default to null] |
| **body** | **File**| Newline-delimited stream of FHIR resources, one serialized resource per line. | |

### Return type

null (empty response body)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: application/fhir+ndjson
- **Accept**: application/fhir+json

