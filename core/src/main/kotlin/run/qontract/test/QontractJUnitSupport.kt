package run.qontract.test

import run.qontract.core.*
import run.qontract.core.utilities.readFile
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.*

open class QontractJUnitSupport {

    @TestFactory()
    fun contractAsTest(): Collection<DynamicTest> {
        var testScenarios: List<Scenario>
        val path = System.getProperty("path")
        val suggestionsPath = System.getProperty("suggestions")
        val contractGherkin = readFile(path)
        val contractBehaviour = ContractBehaviour(contractGherkin)

        testScenarios = if (suggestionsPath.isNullOrEmpty()) {
            contractBehaviour.generateTestScenarios(LinkedList())
        } else {
            val suggestionsGherkin = readFile(suggestionsPath)
            val suggestions = Suggestions(suggestionsGherkin).scenarios
            contractBehaviour.generateTestScenarios(suggestions)
        }
        return testScenarios.map {
            DynamicTest.dynamicTest("$it") {
                val host = System.getProperty("host")
                val port = System.getProperty("port")
                val httpClient = HttpClient("http://$host:$port")
                var result: Result
                httpClient.setServerState(it.serverState)
                val request = it.generateHttpRequest()
                var response: HttpResponse? = null
                result = try {
                    response = httpClient.execute(request)
                    it.matches(response)
                } catch (exception: Throwable) {
                    Result.Failure("Error: ${exception.message}")
                            .also { failure -> failure.updateScenario(it) }
                }
                ResultAssert.assertThat(result).isSuccess(request, response)
            }
        }.toList()
    }

}