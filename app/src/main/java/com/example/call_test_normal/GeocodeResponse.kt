package com.example.call_test_normal

data class GeocodeResponse(
    val status: String,
    val meta: Meta,
    val addresses: List<Address>,
    val errorMessage: String?
) {
    data class Meta(
        val totalCount: Int,
        val page: Int,
        val count: Int
    )

    data class Address(
        val roadAddress: String,
        val jibunAddress: String,
        val englishAddress: String,
        val addressElements: List<AddressElement>,
        val x: String,
        val y: String,
        val distance: Double
    ) {
        data class AddressElement(
            val types: List<String>,
            val longName: String,
            val shortName: String,
            val code: String
        )
    }
}
