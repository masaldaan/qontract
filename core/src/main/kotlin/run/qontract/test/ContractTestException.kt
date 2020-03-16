package run.qontract.test

class ContractTestException(message: String?) : Exception(message) {
    companion object {
        @JvmStatic
        fun missingParam(missingValue: String): ContractTestException {
            return ContractTestException("$missingValue is missing. Can't generate the contract test.")
        }
    }
}