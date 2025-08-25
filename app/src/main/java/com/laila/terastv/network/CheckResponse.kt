data class CheckResponse(
    val registered: Boolean,
    val data: SchoolData? = null
)

data class SchoolData(
    val NPSN: String,
    val school_name: String,
    val sn_tv: String
)