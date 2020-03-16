package run.qontract.core

import run.qontract.core.pattern.NumericStringPattern
import run.qontract.core.pattern.PatternMismatchException
import run.qontract.core.pattern.PatternTable.Companion.fromPSV
import run.qontract.core.pattern.StringPattern
import run.qontract.core.pattern.asValue
import run.qontract.test.TestExecutor
import io.cucumber.gherkin.GherkinDocumentBuilder
import io.cucumber.gherkin.Parser
import io.cucumber.messages.IdGenerator
import io.cucumber.messages.IdGenerator.Incrementing
import io.cucumber.messages.Messages.GherkinDocument
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.lang.NumberFormatException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContractAsTestWithSamplesInTable {
    @Test
    fun parseTabularBackground() {
        val scenarioOutlineGherkin = "Feature: Contract for /balance API\n\n" +
                "  Background: \n" +
                "    | account_id | calls_left | messages_left | \n" +
                "    | 10 | 20 | 30 | \n\n" +
                "  Scenario: \n\n" +
                "    When GET /balance?account_id=10\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: 20, messages_left: 30}\n\n" +
                "    Examples:\n" +
                "    | account-id | calls-left | messages-left | \n" +
                "    | 10 | 20 | 30 | " +
                ""
        val background = toGherkinDocument(scenarioOutlineGherkin).feature.childrenList[0].background
        val description = background.description
        val table = fromPSV(description)
        Assertions.assertEquals("10", table.getRow(0).getField("account_id"))
    }

    @Test
    fun tabularDataParsing() {
        val background = "" +
                "    | account-id | calls-left | messages-left | \n" +
                "    | 10 | 20 | 30 | " +
                ""
        val table = fromPSV(background)
        Assertions.assertEquals("10", table.getRow(0).getField("account-id"))
    }

    @Test
    @Throws(Throwable::class)
    fun GETAndResponseBodyGeneratedThroughDataTableWithPathParams() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Background: \n" +
                "    | account_id | calls_left | messages_left | \n" +
                "    | 10 | 20 | 30 | \n" +
                "    | hello | 30 | 40 | \n" +
                "  Scenario: \n" +
                "    When GET /balance/(account_id:number)\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: \"(number)\", messages_left: \"(number)\"}" +
                ""
        Assertions.assertThrows(ContractParseException::class.java) { jsonResponsesTestsShouldBeVerifiedAgainstTable(contractGherkin) }
    }

    @Throws(Throwable::class)
    private fun jsonResponsesTestsShouldBeVerifiedAgainstTable(contractGherkin: String) {
        val contractBehaviour = ContractBehaviour(contractGherkin)
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                var account_id = request.queryParams["account_id"]
                if (account_id == null) {
                    val pathParts = request.path!!.split("/".toRegex()).toTypedArray()
                    account_id = pathParts[pathParts.size - 1]
                }
                Assertions.assertEquals("GET", request.method)
                Assertions.assertTrue(NumericStringPattern().matches(asValue(account_id), Resolver()) is Result.Success)
                val headers: HashMap<String, String?> = object : HashMap<String, String?>() {
                    init {
                        put("Content-Type", "application/json")
                    }
                }
                var jsonResponseString: String? = null
                if (account_id == "10") {
                    jsonResponseString = "{calls_left: 20, messages_left: 30}"
                } else if (account_id == "20") {
                    jsonResponseString = "{calls_left: 30, messages_left: \"hello\"}"
                }
                return HttpResponse(200, jsonResponseString, headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
    }

    @Test
    @Throws(Throwable::class)
    fun POSTBodyAndResponseGeneratedThroughDataTable() {
        val contractGherkin = """
Feature: Contract for /balance API

  Scenario Outline: 
    When POST /account
    And request-body {"name": "(string)", "city": "(string)"}
    Then status 200
    And response-body {"account_id": "(number)"}

  Examples:
  | account_id | name | city | 
  | 10 | John Doe | Mumbai | 
  | 20 | Jane Doe | Bangalore | 
"""
        jsonRequestAndResponseTest(contractGherkin)
    }

    @Test
    @Throws(Throwable::class)
    fun `Examples in multiple tables should be used`() {
        val contractGherkin = """
Feature: Contract for /balance API

  Scenario Outline: 
    When POST /account
    And request-body {"name": "(string)", "city": "(string)"}
    Then status 200
    And response-body {"account_id": "(number)"}

  Examples:
  | account_id | name | city | 
  | 10 | John Doe | Mumbai | 
  
  Examples:
  | account_id | name | city | 
  | 20 | Jane Doe | Bangalore | 
"""
        jsonRequestAndResponseTest(contractGherkin)
    }

    @Test
    @Throws(Throwable::class)
    fun `Example values are picked up in the keys of json objects defined in lazy patterns`() {
        val contractGherkin = """
Feature: Contract for /balance API

  Scenario Outline: 
    Given pattern Person {"name": "(string)", "address": "(Address)"}
    And pattern Address {"city": "(string)"}
    When POST /account
    And request-body (Person)
    Then status 200
    And response-body {"account_id": "(number)"}

  Examples:
  | account_id | name | city | 
  | 10 | John Doe | Mumbai | 
  | 20 | Jane Doe | Bangalore | 
"""

        val contractBehaviour = ContractBehaviour(contractGherkin)

        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val requestJSON = request.body!!.value as Map<String, Any?>
                val name = requestJSON["name"]
                val city = (requestJSON["address"] as Map<String, Any?>)["city"]
                Assertions.assertEquals("POST", request.method)
                Assertions.assertTrue(StringPattern().matches(asValue(city), Resolver()) is Result.Success)
                val headers: HashMap<String, String?> = object : HashMap<String, String?>() {
                    init {
                        put("Content-Type", "application/json")
                    }
                }

                if(name !in listOf("John Doe", "Jane Doe"))
                    throw Exception("Unexpected name $name")

                when (name) {
                    "John Doe" -> assertEquals("Mumbai", city)
                    "Jane Doe" -> assertEquals("Bangalore", city)
                }

                val jsonResponseString: String? = when (name) {
                    "John Doe" -> "{account_id: 10}"
                    else -> "{account_id: 20}"
                }

                return HttpResponse(200, jsonResponseString, headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {
            }
        })
    }

    @Throws(Throwable::class)
    private fun jsonRequestAndResponseTest(contractGherkin: String) {
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val flags = mutableMapOf("john" to false, "jane" to false)

        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val requestJSON = request.body!!.value as Map<String, Any?>
                val name = requestJSON["name"]
                val city = requestJSON["city"]
                Assertions.assertEquals("POST", request.method)
                Assertions.assertTrue(StringPattern().matches(asValue(name), Resolver()) is Result.Success)
                Assertions.assertTrue(StringPattern().matches(asValue(city), Resolver()) is Result.Success)
                val headers: HashMap<String, String?> = object : HashMap<String, String?>() {
                    init {
                        put("Content-Type", "application/json")
                    }
                }
                var jsonResponseString: String? = null
                if (name == "John Doe") {
                    flags["john"] = true;
                    jsonResponseString = "{account_id: 10}"
                } else if (name == "Jane Doe") {
                    flags["jane"] = true;
                    jsonResponseString = "{account_id: 20}"
                }
                return HttpResponse(200, jsonResponseString, headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {
            }
        })

        assertTrue(flags["john"] ?: false)
        assertTrue(flags["jane"] ?: false)
    }

    @Test
    @Throws(Throwable::class)
    fun POSTBodyAndResponseXMLGeneratedThroughDataTable() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Background: \n" +
                "    | account_id | name | city | \n" +
                "    | 10 | John Doe | Mumbai | \n" +
                "    | 20 | Jane Doe | Bangalore | \n" +
                "  Scenario: \n" +
                "    When POST /account\n" +
                "    And request-body <account><name>(string)</name><city>(string)</city></account>\n" +
                "    Then status 200\n" +
                "    And response-body <account><account_id>(number)</account_id></account>" +
                ""
        xmlRequestAndResponseTest(contractGherkin)
    }

    @Throws(Throwable::class)
    private fun xmlRequestAndResponseTest(contractGherkin: String) {
        val contractBehaviour = ContractBehaviour(contractGherkin)
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val requestXML = request.body!!.value as Document
                val root: Node = requestXML.documentElement
                val nameItem = root.childNodes.item(0)
                val cityItem = root.childNodes.item(1)
                Assertions.assertEquals("name", nameItem.nodeName)
                Assertions.assertEquals("city", cityItem.nodeName)
                val name = nameItem.firstChild.nodeValue
                Assertions.assertTrue(StringPattern().matches(asValue(name), Resolver()) is Result.Success)
                Assertions.assertTrue(StringPattern().matches(asValue(cityItem.firstChild.nodeValue), Resolver()) is Result.Success)
                Assertions.assertEquals("POST", request.method)
                val headers: HashMap<String, String?> = object : HashMap<String, String?>() {
                    init {
                        put("Content-Type", "application/xml")
                    }
                }
                var xmlResponseString: String? = null
                if (name == "John Doe") {
                    xmlResponseString = "<account><account_id>10</account_id></account>"
                } else if (name == "Jane Doe") {
                    xmlResponseString = "<account><account_id>20</account_id></account>"
                }
                return HttpResponse(200, xmlResponseString, headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
    }

    companion object {
        private fun toGherkinDocument(gherkinData: String): GherkinDocument {
            val idGenerator: IdGenerator = Incrementing()
            val parser = Parser(GherkinDocumentBuilder(idGenerator))
            return parser.parse(gherkinData).build()
        }
    }
}