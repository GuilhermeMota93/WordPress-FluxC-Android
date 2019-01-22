package org.wordpress.android.fluxc.store.stats.time

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.Dispatchers.Unconfined
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.stats.time.ClicksModel
import org.wordpress.android.fluxc.model.stats.time.TimeStatsMapper
import org.wordpress.android.fluxc.network.rest.wpcom.stats.time.ClicksRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.stats.time.ClicksRestClient.ClicksResponse
import org.wordpress.android.fluxc.network.utils.StatsGranularity.DAYS
import org.wordpress.android.fluxc.persistence.stats.TimeStatsSqlUtils
import org.wordpress.android.fluxc.store.stats.StatsStore.FetchStatsPayload
import org.wordpress.android.fluxc.store.stats.StatsStore.StatsError
import org.wordpress.android.fluxc.store.stats.StatsStore.StatsErrorType.API_ERROR
import org.wordpress.android.fluxc.test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val PAGE_SIZE = 8
private val DATE = Date(0)

@RunWith(MockitoJUnitRunner::class)
class ClicksStoreTest {
    @Mock lateinit var site: SiteModel
    @Mock lateinit var restClient: ClicksRestClient
    @Mock lateinit var sqlUtils: TimeStatsSqlUtils
    @Mock lateinit var mapper: TimeStatsMapper
    private lateinit var store: ClicksStore
    @Before
    fun setUp() {
        store = ClicksStore(
                restClient,
                sqlUtils,
                mapper,
                Unconfined
        )
    }

    @Test
    fun `returns clicks per site`() = test {
        val fetchInsightsPayload = FetchStatsPayload(
                CLICKS_RESPONSE
        )
        val forced = true
        whenever(restClient.fetchClicks(site, DAYS, DATE, PAGE_SIZE + 1, forced)).thenReturn(
                fetchInsightsPayload
        )
        val model = mock<ClicksModel>()
        whenever(mapper.map(CLICKS_RESPONSE, PAGE_SIZE)).thenReturn(model)

        val responseModel = store.fetchClicks(site, PAGE_SIZE, DAYS, DATE, forced)

        assertThat(responseModel.model).isEqualTo(model)
        verify(sqlUtils).insert(site, CLICKS_RESPONSE, DAYS, DATE)
    }

    @Test
    fun `returns error when clicks call fail`() = test {
        val type = API_ERROR
        val message = "message"
        val errorPayload = FetchStatsPayload<ClicksResponse>(StatsError(type, message))
        val forced = true
        whenever(restClient.fetchClicks(site, DAYS, DATE, PAGE_SIZE + 1, forced)).thenReturn(errorPayload)

        val responseModel = store.fetchClicks(site, PAGE_SIZE, DAYS, DATE, forced)

        assertNotNull(responseModel.error)
        val error = responseModel.error!!
        assertEquals(type, error.type)
        assertEquals(message, error.message)
    }

    @Test
    fun `returns clicks from db`() {
        whenever(sqlUtils.selectClicks(site, DAYS, DATE)).thenReturn(CLICKS_RESPONSE)
        val model = mock<ClicksModel>()
        whenever(mapper.map(CLICKS_RESPONSE, PAGE_SIZE)).thenReturn(model)

        val result = store.getClicks(site, DAYS, PAGE_SIZE, DATE)

        assertThat(result).isEqualTo(model)
    }
}
