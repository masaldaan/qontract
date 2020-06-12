package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import run.qontract.conversions.*
import run.qontract.core.pattern.parsedJSONStructure
import run.qontract.core.value.EmptyString
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.NumberValue
import run.qontract.stub.HttpStub

class PostmanKtTests {
    @Test
    fun `Postman to Qontract with raw format request body`() {
        val postmanContent = """{
	"info": {
		"_postman_id": "8bd0c42a-983a-492e-99bd-a2f1936bc02e",
		"name": "Test API",
		"schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
	},
	"item": [
		{
			"name": "With no body or params",
			"request": {
				"method": "GET",
				"header": [],
				"url": {
					"raw": "http://localhost:9000/stuff",
					"protocol": "http",
					"host": [
						"localhost"
					],
					"port": "9000",
					"path": [
						"stuff"
					]
				}
			},
			"response": []
		},
		{
			"name": "With JSON body",
			"request": {
				"method": "POST",
				"header": [],
				"body": {
					"mode": "raw",
					"raw": "{\"data\": \"value\"}",
					"options": {
						"raw": {
							"language": "json"
						}
					}
				},
				"url": {
					"raw": "http://localhost:9000/stuff",
					"protocol": "http",
					"host": [
						"localhost"
					],
					"port": "9000",
					"path": [
						"stuff"
					]
				}
			},
			"response": []
		},
		{
			"name": "With query",
			"request": {
				"method": "GET",
				"header": [],
				"url": {
					"raw": "http://localhost:9000/stuff?one=1",
					"protocol": "http",
					"host": [
						"localhost"
					],
					"port": "9000",
					"path": [
						"stuff"
					],
					"query": [
						{
							"key": "one",
							"value": "1"
						}
					]
				}
			},
            "response": [
				{
					"name": "Square Of A Number 2",
					"originalRequest": {
						"method": "POST",
						"header": [],
						"body": {
							"mode": "raw",
							"raw": "10",
							"options": {
								"raw": {
									"language": "text"
								}
							}
						},
						"url": {
							"raw": "http://localhost:9000/square",
							"protocol": "http",
							"host": [
								"localhost"
							],
							"port": "9000",
							"path": [
								"square"
							]
						}
					},
					"status": "OK",
					"code": 200,
					"_postman_previewlanguage": "plain",
					"header": [
						{
							"key": "Vary",
							"value": "Origin"
						},
						{
							"key": "X-Qontract-Result",
							"value": "success"
						},
						{
							"key": "Content-Length",
							"value": "3"
						},
						{
							"key": "Content-Type",
							"value": "text/plain"
						},
						{
							"key": "Connection",
							"value": "1000"
						}
					],
					"cookie": [],
					"body": "100"
				}
            ]
        },
		{
			"name": "With form fields",
			"request": {
				"method": "POST",
				"header": [],
				"body": {
					"mode": "urlencoded",
					"urlencoded": [
						{
							"key": "field1",
							"value": "10",
							"type": "text"
						}
					]
				},
				"url": {
					"raw": "http://localhost:9000/stuff",
					"protocol": "http",
					"host": [
						"localhost"
					],
					"port": "9000",
					"path": [
						"stuff"
					]
				}
			},
			"response": []
		},
		{
			"name": "With form data",
			"request": {
				"method": "POST",
				"header": [],
				"body": {
					"mode": "formdata",
					"formdata": [
						{
							"key": "part1",
							"value": "10",
							"type": "text"
						}
					]
				},
				"url": {
					"raw": "http://localhost:9000/stuff",
					"protocol": "http",
					"host": [
						"localhost"
					],
					"port": "9000",
					"path": [
						"stuff"
					]
				}
			},
			"response": []
		}
	],
	"protocolProfileBehavior": {}
}"""

        val info = postmanCollectionToGherkin(postmanContent)
        val (_, gherkinString, _, stubs) = info.first()

        println(gherkinString)

        val expectedGherkinString = """Feature: Test API
  Scenario: With no body or params
    When GET /stuff
    Then status 200
    And response-header Connection (string)
    And response-body (number)
  
  Scenario: With JSON body
    Given type RequestBody
      | data | (string) |
    When POST /stuff
    And request-body (RequestBody)
    Then status 200
    And response-header Connection (string)
    And response-body (number)
  
    Examples:
    | data |
    | value |
  
  Scenario: With query
    When GET /stuff?one=(number)
    Then status 200
    And response-header Connection (string)
    And response-body (number)
  
    Examples:
    | one |
    | 1 |
  
  Scenario: Square Of A Number 2
    When POST /square
    And request-body (RequestBody: number)
    Then status 200
    And response-header Connection (number)
    And response-body (number)
  
    Examples:
    | RequestBody |
    | 10 |
  
  Scenario: With form fields
    When POST /stuff
    And form-field field1 (number)
    Then status 200
    And response-header Connection (string)
    And response-body (number)
  
    Examples:
    | field1 |
    | 10 |
  
  Scenario: With form data
    When POST /stuff
    And request-part part1 (number)
    Then status 200
    And response-header Connection (string)
    And response-body (number)
  
    Examples:
    | part1 |
    | 10 |"""

        assertThat(gherkinString.trim()).isEqualTo(expectedGherkinString.trim())

        validate(gherkinString, stubs)
    }

    @Test
    fun `should convert response info to an http response`() {
        val response = postmanItemResponse(parsedJSONStructure("""				{
					"name": "Square Of A Number 2",
					"status": "OK",
					"code": 200,
					"_postman_previewlanguage": "plain",
					"header": [
						{
							"key": "X-Header",
							"value": "right value"
						}
					],
					"cookie": [],
					"body": "100"
				}""") as JSONObjectValue)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo(NumberValue(100))
        assertThat(response.headers).hasSize(1)
        assertThat(response.headers.getOrDefault("X-Header", "does not exist")).isEqualTo("right value")
    }

    @Test
    fun `postman response conversion to qontract`() {
        val namedStubs = namedStubsFromPostmanResponses((parsedJSONStructure("""[
				{
					"name": "Square Of A Number 2",
					"originalRequest": {
						"method": "POST",
						"header": [],
						"body": {
							"mode": "raw",
							"raw": "10",
							"options": {
								"raw": {
									"language": "text"
								}
							}
						},
						"url": {
							"raw": "http://localhost:9000/square",
							"protocol": "http",
							"host": [
								"localhost"
							],
							"port": "9000",
							"path": [
								"square"
							]
						}
					},
					"status": "OK",
					"code": 200,
					"_postman_previewlanguage": "plain",
					"header": [
						{
							"key": "X-Header",
							"value": "right value"
						}
					],
					"cookie": [],
					"body": "100"
				}
            ]""") as JSONArrayValue).list)

        assertThat(namedStubs).hasSize(1)

        val namedStub = namedStubs.first()
        assertThat(namedStub.second.name).isEqualTo("Square Of A Number 2")

        val request = namedStub.second.stub.request
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/square")
        assertThat(request.headers).hasSize(0)
        assertThat(request.body).isEqualTo(NumberValue(10))

        val response = namedStub.second.stub.response
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo(NumberValue(100))
        assertThat(response.headers).hasSize(1)
        assertThat(response.headers.getOrDefault("X-Header", "does not exist")).isEqualTo("right value")
    }

    private fun validate(gherkinString: String, stubs: List<NamedStub>) {
        val behaviour = Feature(gherkinString)
        val cleanedUpStubs = stubs.map { it.stub }.map { it.copy(response = dropContentAndCORSResponseHeaders(it.response) ) }
        for(stub in cleanedUpStubs) {
            behaviour.matchingStubResponse(stub)
        }
    }

    @Test
    fun `postman request with body to HttpRequest object with body`() {
        val (baseURL, request) = postmanItemRequest(parsedJSONStructure("""{
						"method": "POST",
						"header": [],
						"body": {
							"mode": "raw",
							"raw": "10",
							"options": {
								"raw": {
									"language": "text"
								}
							}
						},
						"url": {
							"raw": "http://localhost:9000/square",
							"protocol": "http",
							"host": [
								"localhost"
							],
							"port": "9000",
							"path": [
								"square"
							]
						}
					}""") as JSONObjectValue)

        assertThat(baseURL).isEqualTo("http://localhost:9000")

        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/square")
        assertThat(request.headers).isEmpty()
        assertThat(request.body).isEqualTo(NumberValue(10))
    }

    @Test
    fun `postman request with headers to HttpRequest object with body`() {
        val (baseURL, request) = postmanItemRequest(parsedJSONStructure("""{
						"method": "GET",
						"header": [
                            {
                                "key": "X-Header",
                                "value": "data"
                            }
                        ],
						"url": {
							"raw": "http://localhost:9000/square",
							"protocol": "http",
							"host": [
								"localhost"
							],
							"port": "9000",
							"path": [
								"square"
							]
						}
					}""") as JSONObjectValue)

        assertThat(baseURL).isEqualTo("http://localhost:9000")

        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/square")
        assertThat(request.headers.getValue("X-Header")).isEqualTo("data")
        assertThat(request.body).isEqualTo(EmptyString)
    }

    @Test
    fun `postman request with form fields to HttpRequest object with form fields`() {
        val (baseURL, request) = postmanItemRequest(parsedJSONStructure("""{
				"method": "POST",
				"header": [],
				"body": {
					"mode": "urlencoded",
					"urlencoded": [
						{
							"key": "field1",
							"value": "10",
							"type": "text"
						}
					]
				},
				"url": {
					"raw": "http://localhost:9000/stuff",
					"protocol": "http",
					"host": [
						"localhost"
					],
					"port": "9000",
					"path": [
						"stuff"
					]
				}
            }""") as JSONObjectValue)

        assertThat(baseURL).isEqualTo("http://localhost:9000")

        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/stuff")
        assertThat(request.body).isEqualTo(EmptyString)
        assertThat(request.formFields.getOrDefault("field1", "does not exist")).isEqualTo("10")
    }

    @Test
    fun `postman request with form data to HttpRequest object with form data content`() {
        val (baseURL, request) = postmanItemRequest(parsedJSONStructure("""{
				"method": "POST",
				"header": [],
				"body": {
					"mode": "formdata",
					"formdata": [
						{
							"key": "part1",
							"value": "10",
							"type": "text"
						}
					]
				},
				"url": {
					"raw": "http://localhost:9000/stuff",
					"protocol": "http",
					"host": [
						"localhost"
					],
					"port": "9000",
					"path": [
						"stuff"
					]
				}
			}""") as JSONObjectValue)

        assertThat(baseURL).isEqualTo("http://localhost:9000")

        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/stuff")
        assertThat(request.body).isEqualTo(EmptyString)
        assertThat(request.formFields).isEmpty()
        val part = request.multiPartFormData.first() as MultiPartContentValue
        assertThat(part.name).isEqualTo("part1")
        assertThat(part.content).isEqualTo(NumberValue(10))
    }

    @Test
    fun `parses an items list inside postman with request and response into multiple scenarios`() {
        val postmanString = """{
            "info": {
				"name": "Test collection",
				"schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
            },
            "item": [{
			"name": "With query",
			"request": {
				"method": "GET",
				"header": [],
				"url": {
					"raw": "http://localhost:9000/stuff?one=1",
					"protocol": "http",
					"host": [
						"localhost"
					],
					"port": "9000",
					"path": [
						"stuff"
					],
					"query": [
						{
							"key": "one",
							"value": "1"
						}
					]
				}
			},
            "response": [
				{
					"name": "Square Of A Number 2",
					"originalRequest": {
						"method": "POST",
						"header": [],
						"url": {
							"raw": "http://localhost:9000/square",
							"protocol": "http",
							"host": [
								"localhost"
							],
							"port": "9000",
							"path": [
								"square"
							]
						}
					},
					"status": "OK",
					"code": 200,
					"_postman_previewlanguage": "plain",
					"header": [
						{
							"key": "Vary",
							"value": "Origin"
						},
						{
							"key": "X-Qontract-Result",
							"value": "success"
						},
						{
							"key": "Content-Length",
							"value": "3"
						},
						{
							"key": "Content-Type",
							"value": "text/plain"
						},
						{
							"key": "Connection",
							"value": "1000"
						}
					],
					"cookie": [],
					"body": "100"
				}
            ]
        }]}"""

        val postmanCollection = stubsFromPostmanCollection(postmanString)

        val stub1 = postmanCollection.stubs[0]
        assertThat(stub1.second.name).isEqualTo("With query")
        assertThat(stub1.second.stub.request.method).isEqualTo("GET")
        assertThat(stub1.second.stub.request.queryParams.getOrDefault("one", "not found")).isEqualTo("1")

        val stub2 = postmanCollection.stubs[1]
        assertThat(stub2.second.name).isEqualTo("Square Of A Number 2")
        assertThat(stub2.second.stub.request.method).isEqualTo("POST")
        assertThat(stub2.second.stub.response.headers).hasSize(5)
    }

    companion object {
        var stub: HttpStub? = null

        @BeforeAll
        @JvmStatic
        fun setup() {
            val gherkin = """Feature: Number API

Scenario: With no body or params
  When GET /stuff
  Then status 200
  And response-body (number)

Scenario: With query
  When GET /stuff?one=(string)
  Then status 200
  And response-body (number)

Scenario: With JSON body
  Given type Request
    | data | (string) |
  When POST /stuff
  And request-body (Request)
  Then status 200
  And response-body (number)

Scenario: With form fields
  When POST /stuff
  And form-field field1 (string)
  Then status 200
  And response-body (number)

Scenario: With form data
  When POST /stuff
  And request-part part1 (string)
  Then status 200
  And response-body (number)
"""

            val contractBehaviour = Feature(gherkin)
            stub = HttpStub(contractBehaviour)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            stub?.close()
        }
    }
}
