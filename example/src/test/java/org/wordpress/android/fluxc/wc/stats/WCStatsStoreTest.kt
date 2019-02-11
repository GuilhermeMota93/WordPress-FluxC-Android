package org.wordpress.android.fluxc.wc.stats

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import com.yarolegovich.wellsql.WellSql
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.SingleStoreWellSqlConfigForTests
import org.wordpress.android.fluxc.generated.WCStatsActionBuilder
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.WCOrderStatsModel
import org.wordpress.android.fluxc.network.rest.wpcom.wc.orderstats.OrderStatsRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.wc.orderstats.OrderStatsRestClient.OrderStatsApiUnit
import org.wordpress.android.fluxc.persistence.WCStatsSqlUtils
import org.wordpress.android.fluxc.persistence.WellSqlConfig
import org.wordpress.android.fluxc.store.WCStatsStore
import org.wordpress.android.fluxc.store.WCStatsStore.FetchOrderStatsPayload
import org.wordpress.android.fluxc.store.WCStatsStore.StatsGranularity
import org.wordpress.android.fluxc.store.WCStatsStore.StatsGranularity.WEEKS
import org.wordpress.android.fluxc.utils.DateUtils
import org.wordpress.android.fluxc.utils.SiteUtils.getCurrentDateTimeForSite
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.hamcrest.CoreMatchers.`is` as isEqual

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner::class)
class WCStatsStoreTest {
    private val mockOrderStatsRestClient = mock<OrderStatsRestClient>()
    private val wcStatsStore = WCStatsStore(Dispatcher(), mockOrderStatsRestClient)

    @Before
    fun setUp() {
        val appContext = RuntimeEnvironment.application.applicationContext

        val config = SingleStoreWellSqlConfigForTests(
                appContext, WCOrderStatsModel::class.java,
                WellSqlConfig.ADDON_WOOCOMMERCE
        )
        WellSql.init(config)
        config.reset()
    }

    @Test
    fun testSimpleInsertionAndRetrieval() {
        val orderStatsModel = WCStatsTestUtils.generateSampleStatsModel()

        WCStatsSqlUtils.insertOrUpdateStats(orderStatsModel)

        with(WellSql.select(WCOrderStatsModel::class.java).asModel) {
            assertEquals(1, size)
            assertEquals("day", first().unit)
        }

        // Create a second stats entry for this site
        val orderStatsModel2 =
                WCStatsTestUtils.generateSampleStatsModel(unit = "month", fields = "fake-data", data = "fake-data")
        WCStatsSqlUtils.insertOrUpdateStats(orderStatsModel2)

        with(WellSql.select(WCOrderStatsModel::class.java).asModel) {
            assertEquals(2, size)
            assertEquals("day", first().unit)
            assertEquals("month", get(1).unit)
        }

        // Overwrite an existing entry
        val orderStatsModel3 = WCStatsTestUtils.generateSampleStatsModel()
        WCStatsSqlUtils.insertOrUpdateStats(orderStatsModel3)

        with(WellSql.select(WCOrderStatsModel::class.java).asModel) {
            assertEquals(2, size)
            assertEquals("day", first().unit)
            assertEquals("month", get(1).unit)
        }

        // Add another "day" entry, but for another site
        val orderStatsModel4 = WCStatsTestUtils.generateSampleStatsModel(localSiteId = 8)
        WCStatsSqlUtils.insertOrUpdateStats(orderStatsModel4)

        with(WellSql.select(WCOrderStatsModel::class.java).asModel) {
            assertEquals(3, size)
            assertEquals("day", first().unit)
            assertEquals("month", get(1).unit)
            assertEquals("day", get(2).unit)
        }
    }

    @Test
    fun testFetchDayOrderStatsDate() {
        val plus12SiteDate = SiteModel().apply { timezone = "12" }.let {
            val payload = FetchOrderStatsPayload(it, StatsGranularity.DAYS)
            wcStatsStore.onAction(WCStatsActionBuilder.newFetchOrderStatsAction(payload))

            val timeOnSite = getCurrentDateTimeForSite(it, "yyyy-MM-dd")

            // The date value passed to the network client should match the current date on the site
            val dateArgument = argumentCaptor<String>()
            verify(mockOrderStatsRestClient).fetchStats(any(), any(),
                    dateArgument.capture(), any(), any(), any())
            val siteDate = dateArgument.firstValue
            assertEquals(timeOnSite, siteDate)
            return@let siteDate
        }

        reset(mockOrderStatsRestClient)

        val minus12SiteDate = SiteModel().apply { timezone = "-12" }.let {
            val payload = FetchOrderStatsPayload(it, StatsGranularity.DAYS)
            wcStatsStore.onAction(WCStatsActionBuilder.newFetchOrderStatsAction(payload))

            val timeOnSite = getCurrentDateTimeForSite(it, "yyyy-MM-dd")

            // The date value passed to the network client should match the current date on the site
            val dateArgument = argumentCaptor<String>()
            verify(mockOrderStatsRestClient).fetchStats(any(), any(), dateArgument.capture(), any(), any(), any())
            val siteDate = dateArgument.firstValue
            assertEquals(timeOnSite, siteDate)
            return@let siteDate
        }

        // The two test sites are 24 hours apart, so we are guaranteed to have one site date match the local date,
        // and the other not match it
        val localDate = SimpleDateFormat("yyyy-MM-dd").format(Date())
        assertThat(localDate, anyOf(isEqual(plus12SiteDate), isEqual(minus12SiteDate)))
        assertThat(localDate, anyOf(not(plus12SiteDate), not(minus12SiteDate)))
    }

    @Test
    fun testGetRawStatsForSiteAndUnit() {
        val dayOrderStatsModel = WCStatsTestUtils.generateSampleStatsModel()
        val site = SiteModel().apply { id = dayOrderStatsModel.localSiteId }
        val monthOrderStatsModel =
                WCStatsTestUtils.generateSampleStatsModel(unit = "month", fields = "fake-data", data = "fake-data")
        WCStatsSqlUtils.insertOrUpdateStats(dayOrderStatsModel)
        WCStatsSqlUtils.insertOrUpdateStats(monthOrderStatsModel)

        val site2 = SiteModel().apply { id = 8 }
        val altSiteOrderStatsModel = WCStatsTestUtils.generateSampleStatsModel(localSiteId = site2.id)
        WCStatsSqlUtils.insertOrUpdateStats(altSiteOrderStatsModel)

        val dayOrderStats = WCStatsSqlUtils.getRawStatsForSiteAndUnit(site, OrderStatsApiUnit.DAY)
        assertNotNull(dayOrderStats)
        with(dayOrderStats!!) {
            assertEquals("day", unit)
        }

        val altSiteDayOrderStats = WCStatsSqlUtils.getRawStatsForSiteAndUnit(site2, OrderStatsApiUnit.DAY)
        assertNotNull(altSiteDayOrderStats)

        val monthOrderStatus = WCStatsSqlUtils.getRawStatsForSiteAndUnit(site, OrderStatsApiUnit.MONTH)
        assertNotNull(monthOrderStatus)
        with(monthOrderStatus!!) {
            assertEquals("month", unit)
        }

        val nonExistentSite = WCStatsSqlUtils.getRawStatsForSiteAndUnit(
                SiteModel().apply { id = 88 }, OrderStatsApiUnit.DAY
        )
        assertNull(nonExistentSite)

        val missingData = WCStatsSqlUtils.getRawStatsForSiteAndUnit(site, OrderStatsApiUnit.YEAR)
        assertNull(missingData)
    }

    @Test
    fun testGetStatsForDaysGranularity() {
        val orderStatsModel = WCStatsTestUtils.generateSampleStatsModel()
        val site = SiteModel().apply { id = orderStatsModel.localSiteId }

        WCStatsSqlUtils.insertOrUpdateStats(orderStatsModel)

        val revenueStats = wcStatsStore.getRevenueStats(site, StatsGranularity.DAYS)
        val orderStats = wcStatsStore.getOrderStats(site, StatsGranularity.DAYS)

        assertTrue(revenueStats.isNotEmpty())
        assertTrue(orderStats.isNotEmpty())
        assertEquals(revenueStats.size, orderStats.size)
        assertEquals(revenueStats.keys, orderStats.keys)
    }

    @Test
    fun testGetStatsCurrencyForSite() {
        val orderStatsModel = WCStatsTestUtils.generateSampleStatsModel()
        val site = SiteModel().apply { id = orderStatsModel.localSiteId }

        assertNull(wcStatsStore.getStatsCurrencyForSite(site))

        WCStatsSqlUtils.insertOrUpdateStats(orderStatsModel)

        assertEquals("USD", wcStatsStore.getStatsCurrencyForSite(site))
    }

    @Test
    fun testGetQuantityForDays() {
        val quantity1 = wcStatsStore.getQuantityByGranularity("2018-01-25", "2018-01-28",
                StatsGranularity.DAYS, 30)
        assertEquals(4, quantity1)

        val quantity2 = wcStatsStore.getQuantityByGranularity("2018-01-01", "2018-01-01",
                StatsGranularity.DAYS, 30)
        assertEquals(1, quantity2)

        val quantity3 = wcStatsStore.getQuantityByGranularity("2018-01-01", "2018-01-31",
                StatsGranularity.DAYS, 30)
        assertEquals(31, quantity3)

        val quantity4 = wcStatsStore.getQuantityByGranularity("2018-01-28", "2018-01-25",
                StatsGranularity.DAYS, 30)
        assertEquals(4, quantity4)

        val quantity5 = wcStatsStore.getQuantityByGranularity("2018-01-01", "2018-01-01",
                StatsGranularity.DAYS, 30)
        assertEquals(1, quantity5)

        val quantity6 = wcStatsStore.getQuantityByGranularity("2018-01-31", "2018-01-01",
                StatsGranularity.DAYS, 30)
        assertEquals(31, quantity6)

        val defaultQuantity1 = wcStatsStore.getQuantityByGranularity("", "",
                StatsGranularity.DAYS, 30)
        assertEquals(30, defaultQuantity1)

        val defaultQuantity2 = wcStatsStore.getQuantityByGranularity(null, null,
                StatsGranularity.DAYS, 30)
        assertEquals(30, defaultQuantity2)
    }

    @Test
    fun testGetQuantityForWeeks() {
        val quantity1 = wcStatsStore.getQuantityByGranularity("2018-10-22", "2018-10-23",
                StatsGranularity.WEEKS, 17)
        assertEquals(1, quantity1)

        val quantity2 = wcStatsStore.getQuantityByGranularity("2017-01-01", "2018-01-01",
                StatsGranularity.WEEKS, 17)
        assertEquals(53, quantity2)

        val quantity3 = wcStatsStore.getQuantityByGranularity("2019-01-20", "2019-01-13",
                StatsGranularity.WEEKS, 17)
        assertEquals(2, quantity3)

        val quantity4 = wcStatsStore.getQuantityByGranularity("2017-01-01", "2018-03-01",
                StatsGranularity.WEEKS, 17)
        assertEquals(61, quantity4)

        val quantity5 = wcStatsStore.getQuantityByGranularity("2018-01-01", "2018-01-31",
                StatsGranularity.WEEKS, 17)
        assertEquals(5, quantity5)

        val quantity6 = wcStatsStore.getQuantityByGranularity("2018-12-01", "2018-12-31",
                StatsGranularity.WEEKS, 17)
        assertEquals(6, quantity6)

        val quantity7 = wcStatsStore.getQuantityByGranularity("2018-11-01", "2018-11-30",
                StatsGranularity.WEEKS, 17)
        assertEquals(5, quantity7)

        val inverseQuantity1 = wcStatsStore.getQuantityByGranularity("2018-10-23", "2018-10-22",
                StatsGranularity.WEEKS, 17)
        assertEquals(1, inverseQuantity1)

        val inverseQuantity2 = wcStatsStore.getQuantityByGranularity("2018-01-01", "2017-01-01",
                StatsGranularity.WEEKS, 17)
        assertEquals(53, inverseQuantity2)

        val inverseQuantity3 = wcStatsStore.getQuantityByGranularity("2019-01-13", "2019-01-20",
                StatsGranularity.WEEKS, 17)
        assertEquals(2, inverseQuantity3)

        val inverseQuantity4 = wcStatsStore.getQuantityByGranularity("2018-03-01", "2017-01-01",
                StatsGranularity.WEEKS, 17)
        assertEquals(61, inverseQuantity4)

        val inverseQuantity5 = wcStatsStore.getQuantityByGranularity("2018-01-31", "2018-01-01",
                StatsGranularity.WEEKS, 17)
        assertEquals(5, inverseQuantity5)

        val inverseQuantity6 = wcStatsStore.getQuantityByGranularity("2018-12-31", "2018-12-01",
                StatsGranularity.WEEKS, 17)
        assertEquals(6, inverseQuantity6)

        val inverseQuantity7 = wcStatsStore.getQuantityByGranularity("2018-11-30", "2018-11-01",
                StatsGranularity.WEEKS, 17)
        assertEquals(5, inverseQuantity7)

        val defaultQuantity1 = wcStatsStore.getQuantityByGranularity("", "",
                StatsGranularity.WEEKS, 17)
        assertEquals(17, defaultQuantity1)

        val defaultQuantity2 = wcStatsStore.getQuantityByGranularity(null, null,
                StatsGranularity.WEEKS, 17)
        assertEquals(17, defaultQuantity2)
    }

    @Test
    fun testGetQuantityForMonths() {
        val quantity1 = wcStatsStore.getQuantityByGranularity("2018-10-22", "2018-10-23",
                StatsGranularity.MONTHS, 12)
        assertEquals(1, quantity1)

        val quantity2 = wcStatsStore.getQuantityByGranularity("2017-01-01", "2018-01-01",
                StatsGranularity.MONTHS, 12)
        assertEquals(13, quantity2)

        val quantity3 = wcStatsStore.getQuantityByGranularity("2018-01-01", "2018-01-01",
                StatsGranularity.MONTHS, 12)
        assertEquals(1, quantity3)

        val quantity4 = wcStatsStore.getQuantityByGranularity("2017-01-01", "2018-03-01",
                StatsGranularity.MONTHS, 12)
        assertEquals(15, quantity4)

        val quantity5 = wcStatsStore.getQuantityByGranularity("2017-01-01", "2018-01-31",
                StatsGranularity.MONTHS, 12)
        assertEquals(13, quantity5)

        val quantity6 = wcStatsStore.getQuantityByGranularity("2018-12-31", "2019-01-01",
                StatsGranularity.MONTHS, 1)
        assertEquals(2, quantity6)

        val inverseQuantity1 = wcStatsStore.getQuantityByGranularity("2018-10-23", "2018-10-22",
                StatsGranularity.MONTHS, 12)
        assertEquals(1, inverseQuantity1)

        val inverseQuantity2 = wcStatsStore.getQuantityByGranularity("2018-01-01", "2017-01-01",
                StatsGranularity.MONTHS, 12)
        assertEquals(13, inverseQuantity2)

        val inverseQuantity3 = wcStatsStore.getQuantityByGranularity("2018-01-01", "2018-01-01",
                StatsGranularity.MONTHS, 12)
        assertEquals(1, inverseQuantity3)

        val inverseQuantity4 = wcStatsStore.getQuantityByGranularity("2018-03-01", "2017-01-01",
                StatsGranularity.MONTHS, 12)
        assertEquals(15, inverseQuantity4)

        val inverseQuantity5 = wcStatsStore.getQuantityByGranularity("2018-01-31", "2017-01-01",
                StatsGranularity.MONTHS, 12)
        assertEquals(13, inverseQuantity5)

        val inverseQuantity6 = wcStatsStore.getQuantityByGranularity("2019-01-01", "2018-12-31",
                StatsGranularity.MONTHS, 1)
        assertEquals(2, inverseQuantity6)

        val defaultQuantity1 = wcStatsStore.getQuantityByGranularity("", "",
                StatsGranularity.MONTHS, 12)
        assertEquals(12, defaultQuantity1)

        val defaultQuantity2 = wcStatsStore.getQuantityByGranularity(null, null,
                StatsGranularity.MONTHS, 12)
        assertEquals(12, defaultQuantity2)
    }

    @Test
    fun testGetQuantityForYears() {
        val quantity1 = wcStatsStore.getQuantityByGranularity("2017-01-01", "2018-01-01",
                StatsGranularity.YEARS, 1)
        assertEquals(2, quantity1)

        val quantity2 = wcStatsStore.getQuantityByGranularity("2017-01-01", "2018-03-01",
                StatsGranularity.YEARS, 1)
        assertEquals(2, quantity2)

        val quantity3 = wcStatsStore.getQuantityByGranularity("2017-01-01", "2018-01-05",
                StatsGranularity.YEARS, 1)
        assertEquals(2, quantity3)

        val quantity4 = wcStatsStore.getQuantityByGranularity("2017-01-01", "2019-03-01",
                StatsGranularity.YEARS, 1)
        assertEquals(3, quantity4)

        val quantity5 = wcStatsStore.getQuantityByGranularity("2015-03-05", "2017-01-01",
                StatsGranularity.YEARS, 1)
        assertEquals(3, quantity5)

        val quantity6 = wcStatsStore.getQuantityByGranularity("2018-12-31", "2019-01-01",
                StatsGranularity.YEARS, 1)
        assertEquals(2, quantity6)

        val quantity7 = wcStatsStore.getQuantityByGranularity("2019-01-25", "2019-01-25",
                StatsGranularity.YEARS, 1)
        assertEquals(1, quantity7)

        val inverseQuantity1 = wcStatsStore.getQuantityByGranularity("2018-01-01", "2017-01-01",
                StatsGranularity.YEARS, 1)
        assertEquals(2, inverseQuantity1)

        val inverseQuantity2 = wcStatsStore.getQuantityByGranularity("2018-03-01", "2017-01-01",
                StatsGranularity.YEARS, 1)
        assertEquals(2, inverseQuantity2)

        val inverseQuantity3 = wcStatsStore.getQuantityByGranularity("2018-01-05", "2017-01-01",
                StatsGranularity.YEARS, 1)
        assertEquals(2, inverseQuantity3)

        val inverseQuantity4 = wcStatsStore.getQuantityByGranularity("2019-03-01", "2017-01-01",
                StatsGranularity.YEARS, 1)
        assertEquals(3, inverseQuantity4)

        val inverseQuantity5 = wcStatsStore.getQuantityByGranularity("2017-01-01", "2015-03-05",
                StatsGranularity.YEARS, 1)
        assertEquals(3, inverseQuantity5)

        val inverseQuantity6 = wcStatsStore.getQuantityByGranularity("2019-01-01", "2018-12-31",
                StatsGranularity.YEARS, 1)
        assertEquals(2, inverseQuantity6)

        val defaultQuantity1 = wcStatsStore.getQuantityByGranularity("", "",
                StatsGranularity.YEARS, 1)
        assertEquals(1, defaultQuantity1)

        val defaultQuantity2 = wcStatsStore.getQuantityByGranularity(null, null,
                StatsGranularity.YEARS, 1)
        assertEquals(1, defaultQuantity2)
    }

    @Test
    fun testFetchOrderStatsForDaysDate() {
        val startDate = "2019-01-01"
        val endDate = "2019-01-01"
        val plus12SiteDate = SiteModel().apply { timezone = "12" }.let {
            val payload = FetchOrderStatsPayload(it, StatsGranularity.DAYS, startDate, endDate)
            wcStatsStore.onAction(WCStatsActionBuilder.newFetchOrderStatsAction(payload))

            val timeOnSite = DateUtils.getDateTimeForSite(it, "yyyy-MM-dd", endDate)

            // The date value passed to the network client should match the current date on the site
            val dateArgument = argumentCaptor<String>()
            verify(mockOrderStatsRestClient).fetchStats(any(), any(), dateArgument.capture(), any(), any(), any())
            val siteDate = dateArgument.firstValue
            assertEquals(timeOnSite, siteDate)
            return@let siteDate
        }

        reset(mockOrderStatsRestClient)

        val minus12SiteDate = SiteModel().apply { timezone = "-12" }.let {
            val payload = FetchOrderStatsPayload(it, StatsGranularity.DAYS, startDate, endDate)
            wcStatsStore.onAction(WCStatsActionBuilder.newFetchOrderStatsAction(payload))

            val timeOnSite = DateUtils.getDateTimeForSite(it, "yyyy-MM-dd", endDate)

            // The date value passed to the network client should match the current date on the site
            val dateArgument = argumentCaptor<String>()
            verify(mockOrderStatsRestClient).fetchStats(any(), any(), dateArgument.capture(), any(), any(), any())
            val siteDate = dateArgument.firstValue
            assertEquals(timeOnSite, siteDate)
            return@let siteDate
        }

        val localDate = DateUtils.formatDate("YYYY-MM-dd", DateUtils.getDateFromString(endDate))
        assertThat(localDate, anyOf(isEqual(plus12SiteDate), isEqual(minus12SiteDate)))
        assertThat(localDate, anyOf(not(plus12SiteDate), not(minus12SiteDate)))
    }

    @Test
    fun testFetchOrderStatsForWeeksDate() {
        val startDate = "2019-01-25"
        val endDate = "2019-01-28"
        val plus12SiteDate = SiteModel().apply { timezone = "12" }.let {
            val payload = FetchOrderStatsPayload(it, StatsGranularity.WEEKS, startDate, endDate)
            wcStatsStore.onAction(WCStatsActionBuilder.newFetchOrderStatsAction(payload))

            val timeOnSite = DateUtils.getDateTimeForSite(it, "yyyy-'W'ww", endDate)

            // The date value passed to the network client should match the current date on the site
            val dateArgument = argumentCaptor<String>()
            verify(mockOrderStatsRestClient).fetchStats(any(), any(), dateArgument.capture(), any(), any(), any())
            val siteDate = dateArgument.firstValue
            assertEquals(timeOnSite, siteDate)
            return@let siteDate
        }

        reset(mockOrderStatsRestClient)

        val minus12SiteDate = SiteModel().apply { timezone = "-12" }.let {
            val payload = FetchOrderStatsPayload(it, StatsGranularity.WEEKS, startDate, endDate)
            wcStatsStore.onAction(WCStatsActionBuilder.newFetchOrderStatsAction(payload))

            val timeOnSite = DateUtils.getDateTimeForSite(it, "yyyy-'W'ww", endDate)

            // The date value passed to the network client should match the current date on the site
            val dateArgument = argumentCaptor<String>()
            verify(mockOrderStatsRestClient).fetchStats(any(), any(), dateArgument.capture(), any(), any(), any())
            val siteDate = dateArgument.firstValue
            assertEquals(timeOnSite, siteDate)
            return@let siteDate
        }

        val localDate = DateUtils.formatDate("yyyy-'W'ww", DateUtils.getDateFromString(endDate))

        assertThat(localDate, isEqual(plus12SiteDate))
        assertThat(localDate, isEqual(minus12SiteDate))
    }

    @Test
    fun testFetchOrderStatsForMonthsDate() {
        val startDate = "2019-01-25"
        val endDate = "2019-01-28"

        val plus12SiteDate = SiteModel().apply { timezone = "12" }.let {
            val payload = FetchOrderStatsPayload(it, StatsGranularity.MONTHS, startDate, endDate)
            wcStatsStore.onAction(WCStatsActionBuilder.newFetchOrderStatsAction(payload))

            val timeOnSite = DateUtils.getDateTimeForSite(it, "yyyy-MM", endDate)

            // The date value passed to the network client should match the current date on the site
            val dateArgument = argumentCaptor<String>()
            verify(mockOrderStatsRestClient).fetchStats(any(), any(), dateArgument.capture(), any(), any(), any())
            val siteDate = dateArgument.firstValue
            assertEquals(timeOnSite, siteDate)
            return@let siteDate
        }

        reset(mockOrderStatsRestClient)

        val minus12SiteDate = SiteModel().apply { timezone = "-12" }.let {
            val payload = FetchOrderStatsPayload(it, StatsGranularity.MONTHS, startDate, endDate)
            wcStatsStore.onAction(WCStatsActionBuilder.newFetchOrderStatsAction(payload))

            val timeOnSite = DateUtils.getDateTimeForSite(it, "yyyy-MM", endDate)

            // The date value passed to the network client should match the current date on the site
            val dateArgument = argumentCaptor<String>()
            verify(mockOrderStatsRestClient).fetchStats(any(), any(), dateArgument.capture(), any(), any(), any())
            val siteDate = dateArgument.firstValue
            assertEquals(timeOnSite, siteDate)
            return@let siteDate
        }

        val localDate = DateUtils.formatDate("yyyy-MM", DateUtils.getDateFromString(endDate))

        assertThat(localDate, isEqual(plus12SiteDate))
        assertThat(localDate, isEqual(minus12SiteDate))
    }

    @Test
    fun testFetchOrderStatsForYearsDate() {
        val startDate = "2018-12-25"
        val endDate = "2019-01-28"

        val plus12SiteDate = SiteModel().apply { timezone = "12" }.let {
            val payload = FetchOrderStatsPayload(it, StatsGranularity.YEARS, startDate, endDate)
            wcStatsStore.onAction(WCStatsActionBuilder.newFetchOrderStatsAction(payload))

            val timeOnSite = DateUtils.getDateTimeForSite(it, "yyyy", endDate)

            // The date value passed to the network client should match the current date on the site
            val dateArgument = argumentCaptor<String>()
            verify(mockOrderStatsRestClient).fetchStats(any(), any(), dateArgument.capture(), any(), any(), any())
            val siteDate = dateArgument.firstValue
            assertEquals(timeOnSite, siteDate)
            return@let siteDate
        }

        reset(mockOrderStatsRestClient)

        val minus12SiteDate = SiteModel().apply { timezone = "-12" }.let {
            val payload = FetchOrderStatsPayload(it, StatsGranularity.YEARS, startDate, endDate)
            wcStatsStore.onAction(WCStatsActionBuilder.newFetchOrderStatsAction(payload))

            val timeOnSite = DateUtils.getDateTimeForSite(it, "yyyy", endDate)

            // The date value passed to the network client should match the current date on the site
            val dateArgument = argumentCaptor<String>()
            verify(mockOrderStatsRestClient).fetchStats(any(), any(), dateArgument.capture(), any(), any(), any())
            val siteDate = dateArgument.firstValue
            assertEquals(timeOnSite, siteDate)
            return@let siteDate
        }

        val localDate = DateUtils.formatDate("yyyy", DateUtils.getDateFromString(endDate))

        assertThat(localDate, isEqual(plus12SiteDate))
        assertThat(localDate, isEqual(minus12SiteDate))
    }

    @Test
    fun testFetchOrderStatsForDaysQuantity() {
        val startDate = "2019-01-01"
        val endDate = "2019-01-01"

        val siteModel = SiteModel()
        val payload = FetchOrderStatsPayload(siteModel, StatsGranularity.DAYS, startDate, endDate)
        wcStatsStore.onAction(WCStatsActionBuilder.newFetchOrderStatsAction(payload))

        val quantity: Long =
                wcStatsStore.getQuantityByGranularity(startDate, endDate, StatsGranularity.DAYS, 30)

        val quantityArgument = argumentCaptor<Long>()
        verify(mockOrderStatsRestClient)
                .fetchStats(any(), any(), any(), quantityArgument.capture().toInt(), any(), any())

        val calculatedQuantity: Long = quantityArgument.firstValue
        assertEquals(quantity, calculatedQuantity)
    }

    @Test
    fun testFetchOrderStatsForWeeksQuantity() {
        val startDate = "2019-01-25"
        val endDate = "2019-01-28"

        val siteModel = SiteModel()
        val payload = FetchOrderStatsPayload(siteModel, StatsGranularity.WEEKS, startDate, endDate)
        wcStatsStore.onAction(WCStatsActionBuilder.newFetchOrderStatsAction(payload))

        val quantity: Long =
                wcStatsStore.getQuantityByGranularity(startDate, endDate, StatsGranularity.WEEKS, 17)

        val quantityArgument = argumentCaptor<Long>()
        verify(mockOrderStatsRestClient)
                .fetchStats(any(), any(), any(), quantityArgument.capture().toInt(), any(), any())

        val calculatedQuantity: Long = quantityArgument.firstValue
        assertEquals(quantity, calculatedQuantity)
    }

    @Test
    fun testFetchOrderStatsForMonthsQuantity() {
        val startDate = "2018-12-25"
        val endDate = "2019-01-28"

        val siteModel = SiteModel()
        val payload = FetchOrderStatsPayload(siteModel, StatsGranularity.MONTHS, startDate, endDate)
        wcStatsStore.onAction(WCStatsActionBuilder.newFetchOrderStatsAction(payload))

        val quantity: Long =
                wcStatsStore.getQuantityByGranularity(startDate, endDate, StatsGranularity.MONTHS, 12)

        val quantityArgument = argumentCaptor<Long>()
        verify(mockOrderStatsRestClient)
                .fetchStats(any(), any(), any(), quantityArgument.capture().toInt(), any(), any())

        val calculatedQuantity: Long = quantityArgument.firstValue
        assertEquals(quantity, calculatedQuantity)
    }

    @Test
    fun testFetchOrderStatsForYearsQuantity() {
        val startDate = "2018-12-25"
        val endDate = "2019-01-28"

        val siteModel = SiteModel()
        val payload = FetchOrderStatsPayload(siteModel, StatsGranularity.YEARS, startDate, endDate)
        wcStatsStore.onAction(WCStatsActionBuilder.newFetchOrderStatsAction(payload))

        val quantity: Long =
                wcStatsStore.getQuantityByGranularity(startDate, endDate, StatsGranularity.YEARS, 12)

        val quantityArgument = argumentCaptor<Long>()
        verify(mockOrderStatsRestClient)
                .fetchStats(any(), any(), any(), quantityArgument.capture().toInt(), any(), any())

        val calculatedQuantity: Long = quantityArgument.firstValue
        assertEquals(quantity, calculatedQuantity)
    }

    @Test
    fun testInsertionAndRetrievalForCustomStats() {
        /*
         * Test Scenario - I
         * Generate default stats with
         * granularity - DAYS
         * quantity - 30
         * date - current date
         * isCustomField - false
         *
         * 1. This generated data to be inserted to local db
         * 2. The data stored to be queried again to check against this generated data
         * 3. The total size of the local db table = 1
         * */
        val defaultDayOrderStatsModel = WCStatsTestUtils.generateSampleStatsModel()
        WCStatsSqlUtils.insertOrUpdateOrderStats(defaultDayOrderStatsModel)

        val site = SiteModel().apply { id = defaultDayOrderStatsModel.localSiteId }

        val defaultDayOrderStats = WCStatsSqlUtils.getRawStatsForSiteUnitQuantityAndDate(site,
                OrderStatsApiUnit.DAY, defaultDayOrderStatsModel.quantity, defaultDayOrderStatsModel.endDate)

        assertEquals(defaultDayOrderStatsModel.unit, defaultDayOrderStats?.unit)
        assertEquals(defaultDayOrderStatsModel.quantity, defaultDayOrderStats?.quantity)
        assertEquals(defaultDayOrderStatsModel.endDate, defaultDayOrderStats?.endDate)

        with(WellSql.select(WCOrderStatsModel::class.java).asModel) {
            assertEquals(1, size)
        }

        /*
         * Test Scenario - II
         * Generate custom stats for same site:
         * granularity - DAYS
         * quantity - 1
         * date - 2019-01-01
         * isCustomField - true
         *
         * 1. This generated data to be inserted to local db
         * 2. The data stored to be queried again to check against this generated data
         * 3. The total size of the local db table = 2
         * */
        val customDayOrderStatsModel =
                WCStatsTestUtils.generateSampleStatsModel(quantity = "1",
                        endDate = "2019-01-01", startDate = "2018-12-31")

        WCStatsSqlUtils.insertOrUpdateOrderStats(customDayOrderStatsModel)
        val customDayOrderStats = WCStatsSqlUtils.getRawStatsForSiteUnitQuantityAndDate(site,
                OrderStatsApiUnit.DAY, customDayOrderStatsModel.quantity, customDayOrderStatsModel.endDate)

        assertEquals(customDayOrderStatsModel.unit, customDayOrderStats?.unit)
        assertEquals(customDayOrderStatsModel.quantity, customDayOrderStats?.quantity)
        assertEquals(customDayOrderStatsModel.endDate, customDayOrderStats?.endDate)

        with(WellSql.select(WCOrderStatsModel::class.java).asModel) {
            assertEquals(2, size)
        }

        /*
         * Test Scenario - III
         * Overwrite an existing default stats for same site, same unit, same quantity and same date:
         * granularity - DAYS
         * quantity - 30
         * date - current date
         * isCustomField - false
         *
         * 1. This generated data would be updated to the local db (not inserted)
         * 2. The data stored to be queried again to check against this generated data
         * 3. The total size of the local db table = 2 (since no new data is inserted)
         * */
        val defaultDayOrderStatsModel2 = WCStatsTestUtils.generateSampleStatsModel()
        WCStatsSqlUtils.insertOrUpdateOrderStats(defaultDayOrderStatsModel2)
        val defaultDayOrderStats2 = WCStatsSqlUtils.getRawStatsForSiteUnitQuantityAndDate(site,
                OrderStatsApiUnit.DAY, defaultDayOrderStatsModel2.quantity, defaultDayOrderStatsModel2.endDate)

        assertEquals(defaultDayOrderStatsModel2.unit, defaultDayOrderStats2?.unit)
        assertEquals(defaultDayOrderStatsModel2.quantity, defaultDayOrderStats2?.quantity)
        assertEquals(defaultDayOrderStatsModel2.endDate, defaultDayOrderStats2?.endDate)

        with(WellSql.select(WCOrderStatsModel::class.java).asModel) {
            assertEquals(2, size)
        }

        /*
         * Test Scenario - IV
         * Overwrite an existing custom stats for same site, same unit, same quantity and same date:
         * granularity - DAYS
         * quantity - 1
         * date - 2019-01-01
         * isCustomField - true
         *
         * 1. This generated data would be updated to the local db (not inserted)
         * 2. The data stored to be queried again to check against this generated data
         * 3. The total size of the local db table = 2 (since no new data is inserted)
         * */
        val customDayOrderStatsModel2 =
                WCStatsTestUtils.generateSampleStatsModel(quantity = "1", endDate = "2019-01-01", startDate = "2018-12-31")

        WCStatsSqlUtils.insertOrUpdateOrderStats(customDayOrderStatsModel2)
        val customDayOrderStats2 = WCStatsSqlUtils.getRawStatsForSiteUnitQuantityAndDate(site,
                OrderStatsApiUnit.DAY, customDayOrderStatsModel2.quantity, customDayOrderStatsModel2.endDate)

        assertEquals(customDayOrderStatsModel2.unit, customDayOrderStats2?.unit)
        assertEquals(customDayOrderStatsModel2.quantity, customDayOrderStats2?.quantity)
        assertEquals(customDayOrderStatsModel2.endDate, customDayOrderStats2?.endDate)

        with(WellSql.select(WCOrderStatsModel::class.java).asModel) {
            assertEquals(2, size)
        }

        /*
         * Test Scenario - V
         * Overwrite an existing custom stats for same site, same unit, same quantity but different date:
         * granularity - DAYS
         * quantity - 1
         * date - 2018-12-31
         * isCustomField - true
         *
         * 1. The data already stored with isCustomField would be purged.
         * 2. This generated data would be inserted to the local db
         * 2. The data stored to be queried again to check against this generated data
         * 3. The total size of the local db table = 2 (since no old data was purged and new data was inserted)
         * */
        val customDayOrderStatsModel3 =
                WCStatsTestUtils.generateSampleStatsModel(quantity = "1",
                        endDate = "2018-12-31", startDate = "2018-12-31")

        WCStatsSqlUtils.insertOrUpdateOrderStats(customDayOrderStatsModel3)
        val customDayOrderStats3 = WCStatsSqlUtils.getRawStatsForSiteUnitQuantityAndDate(site,
                OrderStatsApiUnit.DAY, customDayOrderStatsModel3.quantity, customDayOrderStatsModel3.endDate)

        assertEquals(customDayOrderStatsModel3.unit, customDayOrderStats3?.unit)
        assertEquals(customDayOrderStatsModel3.quantity, customDayOrderStats3?.quantity)
        assertEquals(customDayOrderStatsModel3.endDate, customDayOrderStats3?.endDate)

        /* expected size of local cache would still be 2 because there can only be one
         * custom stats row stored in local cache at any point of time. Before storing incoming data,
         * the existing data will be purged */
        with(WellSql.select(WCOrderStatsModel::class.java).asModel) {
            assertEquals(2, size)
        }

        /*
         * Test Scenario - VI
         * Generate default stats for same site with different unit:
         * granularity - WEEKS
         * quantity - 17
         * date - current date
         * isCustomField - false
         *
         * 1. This generated data to be inserted to local db
         * 2. The data stored to be queried again to check against this generated data
         * 3. The total size of the local db table = 3 (since stats with DAYS granularity would be stored already)
         * */
        val defaultWeekOrderStatsModel =
                WCStatsTestUtils.generateSampleStatsModel(unit = OrderStatsApiUnit.WEEK.toString(), quantity = "17")

        WCStatsSqlUtils.insertOrUpdateOrderStats(defaultWeekOrderStatsModel)
        val defaultWeekOrderStats = WCStatsSqlUtils.getRawStatsForSiteUnitQuantityAndDate(site,
                OrderStatsApiUnit.WEEK, defaultWeekOrderStatsModel.quantity, defaultWeekOrderStatsModel.endDate)

        assertEquals(defaultWeekOrderStatsModel.unit, defaultWeekOrderStats?.unit)
        assertEquals(defaultWeekOrderStatsModel.quantity, defaultWeekOrderStats?.quantity)
        assertEquals(defaultWeekOrderStatsModel.endDate, defaultWeekOrderStats?.endDate)

        with(WellSql.select(WCOrderStatsModel::class.java).asModel) {
            assertEquals(3, size)
        }

        /*
         * Test Scenario - VII
         * Generate custom stats for same site with different unit:
         * granularity - WEEKS
         * quantity - 2
         * date - 2019-01-28
         * isCustomField - true
         *
         * 1. This generated data to be inserted to local db
         * 2. The data stored to be queried again to check against this generated data
         * 3. The total size of the local db table = 3 (since 2 default stats would be stored
         *    already and previously stored custom stats would be purged)
         * */
        val customWeekOrderStatsModel =
                WCStatsTestUtils.generateSampleStatsModel(unit = OrderStatsApiUnit.WEEK.toString(),
                        quantity = "2", endDate = "2019-01-28", startDate = "2019-01-25")

        WCStatsSqlUtils.insertOrUpdateOrderStats(customWeekOrderStatsModel)
        val customWeekOrderStats = WCStatsSqlUtils.getRawStatsForSiteUnitQuantityAndDate(site,
                OrderStatsApiUnit.WEEK, customWeekOrderStatsModel.quantity, customWeekOrderStatsModel.endDate)

        assertEquals(customWeekOrderStatsModel.unit, customWeekOrderStats?.unit)
        assertEquals(customWeekOrderStatsModel.quantity, customWeekOrderStats?.quantity)
        assertEquals(customWeekOrderStatsModel.endDate, customWeekOrderStats?.endDate)

        with(WellSql.select(WCOrderStatsModel::class.java).asModel) {
            assertEquals(3, size)
        }

        /*
         * Test Scenario - VIII
         * Generate default stats for different site with different unit:
         * siteId - 8
         * granularity - MONTHS
         * quantity - 12
         * date - current date
         * isCustomField - false
         *
         * 1. This generated data to be inserted to local db
         * 2. The data stored to be queried again to check against this generated data
         * 3. The total size of the local db table = 5 (since stats with DAYS and WEEKS granularity
         *    would be stored already)
         * */
        val site2 = SiteModel().apply { id = 8 }
        val defaultMonthOrderStatsModel =
                WCStatsTestUtils.generateSampleStatsModel(localSiteId = site2.id,
                        unit = OrderStatsApiUnit.MONTH.toString(), quantity = "12")

        WCStatsSqlUtils.insertOrUpdateOrderStats(defaultMonthOrderStatsModel)
        val defaultMonthOrderStats = WCStatsSqlUtils.getRawStatsForSiteUnitQuantityAndDate(site2,
                OrderStatsApiUnit.MONTH, defaultMonthOrderStatsModel.quantity, defaultMonthOrderStatsModel.endDate)

        assertEquals(defaultMonthOrderStatsModel.unit, defaultMonthOrderStats?.unit)
        assertEquals(defaultMonthOrderStatsModel.quantity, defaultMonthOrderStats?.quantity)
        assertEquals(defaultMonthOrderStatsModel.endDate, defaultMonthOrderStats?.endDate)

        with(WellSql.select(WCOrderStatsModel::class.java).asModel) {
            assertEquals(4, size)
        }

        /*
         * Test Scenario - IX
         * Generate custom stats for different site with different unit and different date:
         * siteId - 8
         * granularity - MONTHS
         * quantity - 2
         * date - 2019-01-28
         * isCustomField - true
         *
         * 1. This generated data to be inserted to local db
         * 2. The data stored to be queried again to check against this generated data
         * 3. The total size of the local db table = 5 (since 3 default stats for another site would be stored
         *    already and 1 stats for site 8 would be stored). No purging of data would take place
         * */
        val customMonthOrderStatsModel = WCStatsTestUtils.generateSampleStatsModel(localSiteId = site2.id,
                unit = OrderStatsApiUnit.MONTH.toString(), quantity = "2",
                endDate = "2019-01-28", startDate = "2018-12-31")

        WCStatsSqlUtils.insertOrUpdateOrderStats(customMonthOrderStatsModel)
        val customMonthOrderStats = WCStatsSqlUtils.getRawStatsForSiteUnitQuantityAndDate(site2,
                OrderStatsApiUnit.MONTH, customMonthOrderStatsModel.quantity, customMonthOrderStatsModel.endDate)

        assertEquals(customMonthOrderStatsModel.unit, customMonthOrderStats?.unit)
        assertEquals(customMonthOrderStatsModel.quantity, customMonthOrderStats?.quantity)
        assertEquals(customMonthOrderStatsModel.endDate, customMonthOrderStats?.endDate)

        with(WellSql.select(WCOrderStatsModel::class.java).asModel) {
            assertEquals(5, size)
        }

        /*
         * Test Scenario - X
         * Check if the below query returns null
         * */
        val missingData = WCStatsSqlUtils.getRawStatsForSiteUnitQuantityAndDate(site2,
                OrderStatsApiUnit.YEAR, "1", "2019-01-01")

        assertNull(missingData)

        /*
         * Test Scenario - XI
         * Fetch data with only site and granularity:
         * siteId - 8
         * granularity - MONTHS
         *
         * */
        val defaultOrderStats = WCStatsSqlUtils.getRawStatsForSiteUnitQuantityAndDate(site2, OrderStatsApiUnit.MONTH)
        assertNotNull(defaultOrderStats)
        assertEquals(OrderStatsApiUnit.MONTH.toString(), defaultOrderStats?.unit)
    }

    @Test
    fun testGetCustomStatsForDaysGranularity() {
        /*
         * Test Scenario - I
         * Generate default stats with
         * granularity - DAYS
         * quantity - 30
         * date - current date
         * isCustomField - false
         *
         * 1. This generated data to be inserted to local db
         * 2. Get Revenue and Order Stats of the same site and granularity
         * 3. Assert Not Null
         * */
        val defaultDayOrderStatsModel = WCStatsTestUtils.generateSampleStatsModel()
        WCStatsSqlUtils.insertOrUpdateOrderStats(defaultDayOrderStatsModel)

        val site = SiteModel().apply { id = defaultDayOrderStatsModel.localSiteId }

        val defaultDayOrderRevenueStats = wcStatsStore.getRevenueStats(site, StatsGranularity.DAYS)
        val defaultDayOrderStats = wcStatsStore.getOrderStats(site, StatsGranularity.DAYS)
        assertTrue(defaultDayOrderRevenueStats.isNotEmpty())
        assertTrue(defaultDayOrderStats.isNotEmpty())

        /*
         * Test Scenario - II
         * Generate custom stats for same site:
         * granularity - DAYS
         * quantity - 1
         * date - 2019-01-01
         * isCustomField - true
         *
         * 1. This generated data to be inserted to local db
         * 2. Get Revenue and Order Stats of the same site and granularity
         * 3. Assert Not Null
         * */
        val customDayOrderStatsModel =
                WCStatsTestUtils.generateSampleStatsModel(quantity = "1", endDate = "2019-01-01",
                        startDate = "2019-01-01")
        WCStatsSqlUtils.insertOrUpdateOrderStats(customDayOrderStatsModel)

        val customDayOrderRevenueStats = wcStatsStore.getRevenueStats(site, StatsGranularity.DAYS,
                customDayOrderStatsModel.quantity, customDayOrderStatsModel.endDate)

        val customDayOrderStats = wcStatsStore.getOrderStats(site, StatsGranularity.DAYS,
                customDayOrderStatsModel.quantity, customDayOrderStatsModel.endDate)

        assertTrue(customDayOrderRevenueStats.isNotEmpty())
        assertTrue(customDayOrderStats.isNotEmpty())

        /*
         * Test Scenario - III
         * Query for custom stats that is not present in local cache: for same site, same quantity, different date
         * granularity - DAYS
         * quantity - 1
         * date - 2018-12-01
         * isCustomField - true
         *
         * 1. Get Revenue and Order Stats of the same site and granularity
         * 3. Assert Null
         * */
        val customDayOrderStatsModel2 = WCStatsTestUtils.generateSampleStatsModel(quantity = "1",
                endDate = "2018-12-01", startDate = "2018-12-01")
        val customDayOrderRevenueStats2 = wcStatsStore.getRevenueStats(site, StatsGranularity.DAYS,
                customDayOrderStatsModel2.quantity, customDayOrderStatsModel2.endDate)

        val customDayOrderStats2 = wcStatsStore.getOrderStats(site, StatsGranularity.DAYS,
                customDayOrderStatsModel2.quantity, customDayOrderStatsModel2.endDate)

        assertTrue(customDayOrderRevenueStats2.isEmpty())
        assertTrue(customDayOrderStats2.isEmpty())

        /*
         * Test Scenario - IV
         * Query for custom stats that is not present in local cache:
         * for same site, different quantity, different date
         * granularity - DAYS
         * quantity - 30
         * date - 2018-12-01
         * isCustomField - true
         *
         * 1. Get Revenue and Order Stats of the same site and granularity but different quantity, different date
         * 3. Assert Null
         * */
        val customDayOrderStatsModel3 = WCStatsTestUtils.generateSampleStatsModel(quantity = "1",
                endDate = "2018-12-01", startDate = "2018-12-01")

        val customDayOrderRevenueStats3 = wcStatsStore.getRevenueStats(site, StatsGranularity.DAYS,
                customDayOrderStatsModel3.quantity, customDayOrderStatsModel3.endDate)

        val customDayOrderStats3 = wcStatsStore.getOrderStats(site, StatsGranularity.DAYS,
                customDayOrderStatsModel3.quantity, customDayOrderStatsModel3.endDate)

        assertTrue(customDayOrderRevenueStats3.isEmpty())
        assertTrue(customDayOrderStats3.isEmpty())

        /*
         * Test Scenario - IV
         * Generate custom stats for same site with different granularity, same date, same quantity
         * granularity - WEEKS
         * quantity - 1
         * date - 2019-01-01
         * isCustomField - true
         *
         * 1. This generated data to be inserted to local db
         * 2. Get Revenue and Order Stats of the same site and granularity
         * 3. Assert Not Null
         * 4. Now if another query ran for granularity - DAYS, with same date and same quantity:
         *    Assert Null
         * */
        val customWeekOrderStatsModel = WCStatsTestUtils.generateSampleStatsModel(quantity = "1",
                endDate = "2019-01-01", startDate = "2019-01-01",
                unit = OrderStatsApiUnit.fromStatsGranularity(WEEKS).toString())

        WCStatsSqlUtils.insertOrUpdateOrderStats(customWeekOrderStatsModel)

        val customWeekOrderRevenueStats = wcStatsStore.getRevenueStats(site, StatsGranularity.WEEKS,
                customWeekOrderStatsModel.quantity, customWeekOrderStatsModel.endDate)

        val customWeekOrderStats = wcStatsStore.getOrderStats(site, StatsGranularity.WEEKS,
                customWeekOrderStatsModel.quantity, customWeekOrderStatsModel.endDate)

        assertTrue(customWeekOrderRevenueStats.isNotEmpty())
        assertTrue(customWeekOrderStats.isNotEmpty())

        val customDayOrderRevenueStats4 = wcStatsStore.getRevenueStats(site, StatsGranularity.DAYS,
                customDayOrderStatsModel.quantity, customDayOrderStatsModel.endDate)

        val customDayOrderStats4 = wcStatsStore.getOrderStats(site, StatsGranularity.DAYS,
                customDayOrderStatsModel.quantity, customDayOrderStatsModel.endDate)

        assertTrue(customDayOrderRevenueStats4.isEmpty())
        assertTrue(customDayOrderStats4.isEmpty())

        /*
         * Test Scenario - V
         * Generate custom stats for different site with same granularity, same date, same quantity
         * site - 8
         * granularity - WEEKS
         * quantity - 1
         * date - 2019-01-01
         * isCustomField - true
         *
         * 1. This generated data to be inserted to local db
         * 2. Get Revenue and Order Stats of the same site and granularity
         * 3. Assert Not Null
         * 4. Now if scenario IV is run again it assert NOT NULL, since the stats is for different sites
         * */
        val customWeekOrderStatsModel2 = WCStatsTestUtils.generateSampleStatsModel(localSiteId = 8,
                unit = OrderStatsApiUnit.fromStatsGranularity(WEEKS).toString(), quantity = "1",
                endDate = "2019-01-01", startDate = "2019-01-01")

        WCStatsSqlUtils.insertOrUpdateOrderStats(customWeekOrderStatsModel2)

        val customWeekOrderRevenueStats2 = wcStatsStore.getRevenueStats(site, StatsGranularity.WEEKS,
                customWeekOrderStatsModel2.quantity, customWeekOrderStatsModel2.endDate)

        val customWeekOrderStats2 = wcStatsStore.getOrderStats(site, StatsGranularity.WEEKS,
                customWeekOrderStatsModel2.quantity, customWeekOrderStatsModel2.endDate)

        assertTrue(customWeekOrderRevenueStats2.isNotEmpty())
        assertTrue(customWeekOrderStats2.isNotEmpty())

        /* Now if scenario IV is run again it assert NOT NULL, since the stats is for different sites */
        assertTrue(customWeekOrderRevenueStats.isNotEmpty())
        assertTrue(customWeekOrderStats.isNotEmpty())
    }

    @Test
    fun testGetCustomStatsForSite() {
        val defaultDayOrderStatsModel = WCStatsTestUtils.generateSampleStatsModel()
        WCStatsSqlUtils.insertOrUpdateOrderStats(defaultDayOrderStatsModel)

        val site = SiteModel().apply { id = defaultDayOrderStatsModel.localSiteId }
        val customStats = wcStatsStore.getCustomStatsForSite(site)
        assertNull(customStats)

        /*
         * For same site, but for custom stats
         * */
        val customDayOrderStatsModel = WCStatsTestUtils
                .generateSampleStatsModel(unit = StatsGranularity.MONTHS.toString(),
                        quantity = "2", endDate = "2019-01-01", startDate = "2018-12-01")
        WCStatsSqlUtils.insertOrUpdateOrderStats(customDayOrderStatsModel)
        val customStats1 = wcStatsStore.getCustomStatsForSite(site)
        assertEquals(StatsGranularity.MONTHS.toString(), customStats1?.unit)
        assertEquals("2018-12-01", customStats1?.startDate)


        /*
         * For different site, but for custom stats
         * */
        val site2 = SiteModel().apply { id = 8 }
        val customStats2 = wcStatsStore.getCustomStatsForSite(site2)
        assertNull(customStats2)
    }
}
