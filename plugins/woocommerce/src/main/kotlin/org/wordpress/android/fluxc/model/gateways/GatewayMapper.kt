package org.wordpress.android.fluxc.model.gateways

import org.wordpress.android.fluxc.network.rest.wpcom.wc.gateways.GatewayRestClient.GatewayResponse
import javax.inject.Inject

class GatewayMapper
@Inject constructor() {
    fun map(response: GatewayResponse): WCGatewayModel {
        return WCGatewayModel(
                response.gatewayId,
                response.title ?: "",
                response.description ?: "",
                response.order,
                response.enabled,
                response.methodTitle ?: "",
                response.methodDescription ?: "",
                response.features
        )
    }
}
