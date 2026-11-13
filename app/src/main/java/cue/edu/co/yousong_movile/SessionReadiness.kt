package cue.edu.co.yousong_movile

/** Metadata is a slug, never a pasted QR URL, JSON coercion, or credential destination. */
internal fun validatedQrSlug(value: Any?): String? =
    (value as? String)?.takeIf { ClientContract.slug(it) == it && !it.contains('/') }

internal fun canParticipate(place: Establishment): Boolean =
    place.service == "ABIERTO" && place.canJoin

internal fun retainsConsent(previous: PrivacyPolicy?, current: PrivacyPolicy): Boolean =
    previous?.version == current.version

internal fun availabilityLabel(place: Establishment): String {
    val message = when (place.service) {
        "ABIERTO" -> if (place.canJoin) "El local admite participantes." else "El local no admite participantes ahora. Actualiza el estado."
        "PAUSADO" -> "El local está pausado. Espera a que el personal reanude el servicio y actualiza el estado."
        "MANTENIMIENTO" -> "El local está en mantenimiento. Espera a que el personal habilite el servicio y actualiza el estado."
        "CERRADO" -> "El local no admite participantes ahora. Espera a que el servicio esté disponible y actualiza el estado."
        else -> "El estado del servicio no permite participar. Actualiza el estado o consulta al personal."
    }
    // The server supplies an instant, not a local schedule or an inferred reason for closure.
    val opening = place.nextOpening?.takeIf { expiresAtMillis(it) != null }
    return message + (opening?.let { " Próxima apertura: $it." } ?: "")
}

/** Public availability has its own freshness; it is not fenced by the queue version. */
internal class SessionReadiness(qrSlug: String?) {
    val slug = validatedQrSlug(qrSlug)
    var availability: Establishment? = null
        private set

    fun accept(place: Establishment): Boolean {
        if (slug == null || place.slug != slug) {
            unavailable()
            return false
        }
        availability = place
        return true
    }

    fun unavailable() { availability = null }

    fun canAct(queue: ServiceState?, participantService: String): Boolean =
        queue == ServiceState.ABIERTO && participantService == "ABIERTO" &&
            availability?.let(::canParticipate) == true

    val label: String get() = if (slug == null) {
        "No se conservó la identidad QR de esta sesión. Puedes consultar la cola. Para volver a participar, sal del local desde Inicio; solo después de una salida confirmada escanea el QR y autoriza F1 de nuevo."
    } else availability?.let(::availabilityLabel)
        ?: "No se pudo confirmar la disponibilidad del local. Puedes consultar la cola; actualiza el estado antes de solicitar o votar."
}
