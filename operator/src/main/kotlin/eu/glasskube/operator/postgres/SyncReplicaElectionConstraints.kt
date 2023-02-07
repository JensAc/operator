package eu.glasskube.operator.postgres

data class SyncReplicaElectionConstraints(
    val enabled: Boolean? = null,
    val nodeLabelsAntiAffinity: List<String>? = null
)
