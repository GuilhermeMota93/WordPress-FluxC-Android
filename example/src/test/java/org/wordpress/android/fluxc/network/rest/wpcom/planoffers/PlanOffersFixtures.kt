package org.wordpress.android.fluxc.network.rest.wpcom.planoffers

import org.wordpress.android.fluxc.model.plans.PlanOfferModel
import org.wordpress.android.fluxc.network.rest.wpcom.planoffers.PlanOffersRestClient.PlanOffersResponse
import org.wordpress.android.fluxc.network.rest.wpcom.planoffers.PlanOffersRestClient.PlanOffersResponse.Feature
import org.wordpress.android.fluxc.network.rest.wpcom.planoffers.PlanOffersRestClient.PlanOffersResponse.Group
import org.wordpress.android.fluxc.network.rest.wpcom.planoffers.PlanOffersRestClient.PlanOffersResponse.Plan
import org.wordpress.android.fluxc.network.rest.wpcom.planoffers.PlanOffersRestClient.PlanOffersResponse.PlanId

val PLAN_OFFER_MODELS = listOf(
        PlanOfferModel(
                listOf(1), listOf(
                PlanOfferModel.Feature("subdomain", "WordPress.com Subdomain", "Subdomain Description"),
                PlanOfferModel.Feature("jetpack-essentials", "JE Features", "JE Description")
        ), "WordPress.com Free",
                "Free",
                "Best for Getting Started",
                "Free description",
                "https://s0.wordpress.com/i/store/mobile/plan-free.png"
        ), PlanOfferModel(
        listOf(1003, 1023), listOf(
        PlanOfferModel.Feature("custom-domain", "Custom Domain Name", "CDN Description"),
        PlanOfferModel.Feature("support-live", "Email & Live Chat Support", "LS Description"),
        PlanOfferModel.Feature("no-ads", "Remove WordPress.com Ads", "No Ads Description")
), "WordPress.com Premium",
        "Premium",
        "Best for Entrepreneurs and Freelancers",
        "Premium description",
        "https://s0.wordpress.com/i/store/mobile/plan-premium.png"
)
)

val PLAN_OFFERS_RESPONSE = PlanOffersResponse(
        listOf(Group("personal", "Personal"), Group("business", "Business")), listOf(
        Plan(
                listOf("personal", "too personal"),
                listOf(PlanId(1)),
                listOf("subdomain", "jetpack-essentials"),
                "WordPress.com Free",
                "Free",
                "Best for Getting Started",
                "Free description",
                "https://s0.wordpress.com/i/store/mobile/plan-free.png"
        ), Plan(
        listOf("business"),
        listOf(PlanId(1003), PlanId(1023)),
        listOf("custom-domain", "support-live", "no-ads"),
        "WordPress.com Premium",
        "Premium",
        "Best for Entrepreneurs and Freelancers",
        "Premium description",
        "https://s0.wordpress.com/i/store/mobile/plan-premium.png"
)
), listOf(
        Feature("subdomain", "WordPress.com Subdomain", "Subdomain Description"),
        Feature("jetpack-essentials", "JE Features", "JE Description"),
        Feature("custom-domain", "Custom Domain Name", "CDN Description"),
        Feature("support-live", "Email & Live Chat Support", "LS Description"),
        Feature("no-ads", "Remove WordPress.com Ads", "No Ads Description")
)
)
