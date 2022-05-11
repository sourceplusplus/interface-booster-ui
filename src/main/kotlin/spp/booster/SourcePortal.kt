package spp.booster

import org.slf4j.LoggerFactory
import spp.protocol.artifact.ArtifactQualifiedName
import spp.protocol.portal.PortalConfiguration
import java.util.*

data class SourcePortal(
    val portalUuid: String,
    val configuration: PortalConfiguration,
) {
    lateinit var viewingArtifact: ArtifactQualifiedName

    companion object {
        private val log = LoggerFactory.getLogger(SourcePortal::class.java)
        private val portalMap = HashMap<String, SourcePortal>()

        fun register(artifactQualifiedName: ArtifactQualifiedName, external: Boolean): String {
            return register(
                UUID.randomUUID().toString(), artifactQualifiedName,
                PortalConfiguration(artifactQualifiedName, external = external)
            )
        }

        fun register(
            portalUuid: String,
            artifactQualifiedName: ArtifactQualifiedName,
            configuration: PortalConfiguration
        ): String {
            val portal = SourcePortal(portalUuid, configuration)
            portal.viewingArtifact = Objects.requireNonNull(artifactQualifiedName)

            portalMap.put(portalUuid, portal)
            log.trace(
                "Registered SourceMarker Portal. Portal UUID: {} - Artifact: {}",
                portalUuid, artifactQualifiedName
            )
            log.trace("Active portals: " + portalMap.size)
            return portalUuid
        }

        fun getPortals(): List<SourcePortal> {
            return ArrayList(portalMap.values)
        }

        fun getPortal(portalUuid: String): SourcePortal? {
            return portalMap.get(portalUuid)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SourcePortal) return false
        if (portalUuid != other.portalUuid) return false
        return true
    }

    override fun hashCode(): Int {
        return portalUuid.hashCode()
    }
}
