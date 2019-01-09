package org.wordpress.android.fluxc.store.stats.time

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import kotlinx.coroutines.experimental.Dispatchers.Unconfined
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.stats.time.TimeStatsMapper
import org.wordpress.android.fluxc.model.stats.time.VisitsAndViewsModel
import org.wordpress.android.fluxc.network.rest.wpcom.stats.time.VisitAndViewsRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.stats.time.VisitAndViewsRestClient.VisitsAndViewsResponse
import org.wordpress.android.fluxc.network.utils.StatsGranularity.DAYS
import org.wordpress.android.fluxc.persistence.TimeStatsSqlUtils
import org.wordpress.android.fluxc.store.StatsStore.FetchStatsPayload
import org.wordpress.android.fluxc.store.StatsStore.StatsError
import org.wordpress.android.fluxc.store.StatsStore.StatsErrorType.API_ERROR
import org.wordpress.android.fluxc.test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val PAGE_SIZE = 8
private val DATE = Date(0)

@RunWith(MockitoJUnitRunner::class)
class VisitsAndViewsStoreTest {
    @Mock lateinit var site: SiteModel
    @Mock lateinit var restClient: VisitAndViewsRestClient
    @Mock lateinit var sqlUtils: TimeStatsSqlUtils
    @Mock lateinit var mapper: TimeStatsMapper
    private lateinit var store: VisitsAndViewsStore
    @Before
    fun setUp() {
        store = VisitsAndViewsStore(
                restClient,
                sqlUtils,
                mapper,
                Unconfined
        )
    }

    @Test
    fun `returns data per site`() = test {
        val fetchInsightsPayload = FetchStatsPayload(
                VISITS_AND_VIEWS_RESPONSE
        )
        val forced = true
        whenever(restClient.fetchVisits(site, DATE, DAYS, PAGE_SIZE, forced)).thenReturn(
                fetchInsightsPayload
        )
        whenever(mapper.map(VISITS_AND_VIEWS_RESPONSE)).thenReturn(VISITS_AND_VIEWS_MODEL)

        val responseModel = store.fetchVisits(site, PAGE_SIZE, DATE, DAYS, forced)

        assertThat(responseModel.model).isEqualTo(VISITS_AND_VIEWS_MODEL)
        verify(sqlUtils).insert(site, VISITS_AND_VIEWS_RESPONSE, DAYS, DATE)
    }

    @Test
    fun `returns error when invalid data`() = test {
        val forced = true
        val fetchInsightsPayload = FetchStatsPayload(
                VISITS_AND_VIEWS_RESPONSE
        )
        whenever(restClient.fetchVisits(site, DATE, DAYS, PAGE_SIZE, forced)).thenReturn(
                fetchInsightsPayload
        )
        val emptyModel = VisitsAndViewsModel("", emptyList())
        whenever(mapper.map(VISITS_AND_VIEWS_RESPONSE)).thenReturn(emptyModel)

        val responseModel = store.fetchVisits(site, PAGE_SIZE, DATE, DAYS, forced)

        assertThat(responseModel.error.type).isEqualTo(INVALID_DATA_ERROR.type)
        assertThat(responseModel.error.message).isEqualTo(INVALID_DATA_ERROR.message)
    }

    @Test
    fun `returns error when data call fail`() = test {
        val type = API_ERROR
        val message = "message"
        val errorPayload = FetchStatsPayload<VisitsAndViewsResponse>(StatsError(type, message))
        val forced = true
        whenever(restClient.fetchVisits(site, DATE, DAYS, PAGE_SIZE, forced)).thenReturn(errorPayload)

        val responseModel = store.fetchVisits(site, PAGE_SIZE, DATE, DAYS, forced)

        assertNotNull(responseModel.error)
        val error = responseModel.error!!
        assertEquals(type, error.type)
        assertEquals(message, error.message)
    }

    @Test
    fun `returns data from db`() {
        whenever(sqlUtils.selectVisitsAndViews(site, DAYS, DATE)).thenReturn(VISITS_AND_VIEWS_RESPONSE)
        val model = mock<VisitsAndViewsModel>()
        whenever(mapper.map(VISITS_AND_VIEWS_RESPONSE)).thenReturn(model)

        val result = store.getVisits(site, DATE, DAYS)

        assertThat(result).isEqualTo(model)
    }
}
