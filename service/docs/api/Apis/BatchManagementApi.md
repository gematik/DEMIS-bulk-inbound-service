# BatchManagementApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**closeBatch**](BatchManagementApi.md#closeBatch) | **POST** /batch/fhir/bundle/{id}/$close | Close an existing batch |
| [**startBatch**](BatchManagementApi.md#startBatch) | **POST** /batch/fhir/bundle | Start a new batch |


<a name="closeBatch"></a>
# **closeBatch**
> closeBatch(id, x-sender)

Close an existing batch

    Closes the batch identified by the given id. Once closed, no further documents can be uploaded. Clients should poll the statistics resource referenced by the Content-Location header after waiting for the duration indicated by the Retry-After header.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **id** | **UUID**| Unique identifier of the batch to close. | [default to null] |
| **x-sender** | **String**| Identifier of the sender that owns the batch. | [default to null] |

### Return type

null (empty response body)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/fhir+json

<a name="startBatch"></a>
# **startBatch**
> String startBatch(x-sender, body)

Start a new batch

    Creates a new batch for the given sender and returns a FHIR Bundle containing the upload location for subsequent document uploads. The request body must be a FHIR resource serialized as application/fhir+json, application/json+fhir or application/json.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **x-sender** | **String**| Identifier of the sender starting the batch. | [default to null] |
| **body** | **String**|  | |

### Return type

**String**

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: application/fhir+json, application/json, application/json+fhir
- **Accept**: application/fhir+json

