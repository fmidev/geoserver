/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.capabilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.TreeSet;
import org.geoserver.catalog.DimensionInfo;
import org.geoserver.catalog.DimensionPresentation;
import org.geoserver.catalog.impl.DimensionInfoImpl;
import org.geoserver.catalog.util.ReaderDimensionsAccessor;
import org.geoserver.util.ISO8601Formatter;
import org.geoserver.wms.WMS;
import org.geoserver.wms.capabilities.DimensionHelper.ElevationDimensionRasterHelper;
import org.geoserver.wms.capabilities.DimensionHelper.Mode;
import org.geoserver.wms.capabilities.DimensionHelper.TemporalDimensionRasterHelper;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.util.Assert;
import org.xml.sax.Attributes;

/**
 * A test for proper ISO8601 formatting.
 *
 * @author Ian Schneider <ischneider@opengeo.org>
 */
public class DimensionHelperTest {

    protected DimensionHelper dimensionHelper;

    @Before
    public void setUp() {

        dimensionHelper = new DimensionHelper(Mode.WMS13, WMS.get()) {

            @Override
            protected void element(String element, String content, Attributes atts) {
                // Capabilities_1_3_0_Translator.this.element(element, content, atts);
            }

            @Override
            protected void element(String element, String content) {
                // Capabilities_1_3_0_Translator.this.element(element, content);
            }
        };
    }

    @Test
    public void testGetCustomDomainRepresentation() {
        final String[] vals = {"value with spaces", "value", "  other values "};
        final List<String> values = new ArrayList<>();
        for (String val : vals) values.add(val);
        DimensionInfo dimensionInfo = new DimensionInfoImpl();
        dimensionInfo.setPresentation(DimensionPresentation.LIST);
        dimensionInfo.setResolution(BigDecimal.valueOf(1));
        String customDimRepr = dimensionHelper.getCustomDomainRepresentation(dimensionInfo, values);
        // value with spaces,value
        Assert.equals(customDimRepr, vals[0] + "," + vals[1] + "," + vals[2].trim());
        // System.out.print(vals.toString());

    }

    @Test
    public void testNegativeYears() {
        ISO8601Formatter fmt = new ISO8601Formatter();

        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeZone(TimeZone.getTimeZone("GMT"));
        cal.clear();

        // base assertion
        cal.set(Calendar.YEAR, 1);
        assertEquals("0001-01-01T00:00:00.000Z", fmt.format(cal.getTime()));

        // according to the spec, the year before is year 0000
        cal.add(Calendar.YEAR, -1);
        assertEquals("0000-01-01T00:00:00.000Z", fmt.format(cal.getTime()));

        // and now where negative territory
        cal.add(Calendar.YEAR, -1);
        assertEquals("-0001-01-01T00:00:00.000Z", fmt.format(cal.getTime()));

        // and real negative
        cal.set(Calendar.YEAR, 265000001);
        assertEquals("-265000000-01-01T00:00:00.000Z", fmt.format(cal.getTime()));
    }

    /**
     * The goal if this test is to verify behavior of a similar, but not complete, format provided by the standard
     * libraries. The incomplete pattern does not support BC dates properly, so we will not test compliance here.
     *
     * <p>The random seed is not specified to allow various test runs broader coverage.
     */
    @Test
    public void testFormatterFuzz() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));

        ISO8601Formatter fmt = new ISO8601Formatter();

        GregorianCalendar cal = new GregorianCalendar();
        Random r = new Random();
        for (int i = 0; i < 1000; i++) {
            cal.set(Calendar.YEAR, 1 + r.nextInt(3000));
            cal.set(Calendar.DAY_OF_YEAR, 1 + r.nextInt(365));
            cal.set(Calendar.HOUR_OF_DAY, r.nextInt(24));
            cal.set(Calendar.MINUTE, r.nextInt(60));
            cal.set(Calendar.SECOND, r.nextInt(60));
            cal.set(Calendar.MILLISECOND, r.nextInt(1000));
            assertEquals(df.format(cal.getTime()), fmt.format(cal.getTime()));
        }
    }

    @Test
    public void testPadding() throws ParseException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        ISO8601Formatter fmt = new ISO8601Formatter();

        assertEquals("0010-01-01T00:01:10.001Z", fmt.format(df.parse("0010-01-01T00:01:10.001")));
        assertEquals("0100-01-01T00:01:10.011Z", fmt.format(df.parse("0100-01-01T00:01:10.011")));
        assertEquals("1000-01-01T00:01:10.111Z", fmt.format(df.parse("1000-01-01T00:01:10.111")));
    }

    @Test
    public void testTemporalDomainListFetchesFullDomain() throws Exception {
        ReaderDimensionsAccessor accessor = mock(ReaderDimensionsAccessor.class);
        TreeSet<Object> fullDomain = new TreeSet<>();
        fullDomain.add(new Date(0));
        fullDomain.add(new Date(1000));
        fullDomain.add(new Date(2000));
        when(accessor.getTimeDomain()).thenReturn(fullDomain);

        DimensionInfo timeInfo = new DimensionInfoImpl();
        timeInfo.setPresentation(DimensionPresentation.LIST);

        TreeSet<Object> domain = new TemporalDimensionRasterHelper(timeInfo, accessor).getDomain();

        assertEquals(fullDomain, domain);
        verify(accessor, never()).getMinTime();
    }

    @Test
    public void testTemporalDomainContinuousIntervalUsesMinMaxOnly() throws Exception {
        ReaderDimensionsAccessor accessor = mock(ReaderDimensionsAccessor.class);
        Date min = new Date(0);
        Date max = new Date(2000);
        when(accessor.getMinTime()).thenReturn(min);
        when(accessor.getMaxTime()).thenReturn(max);

        DimensionInfo timeInfo = new DimensionInfoImpl();
        timeInfo.setPresentation(DimensionPresentation.CONTINUOUS_INTERVAL);

        TreeSet<Object> domain = new TemporalDimensionRasterHelper(timeInfo, accessor).getDomain();

        assertEquals(new TreeSet<>(List.of(min, max)), domain);
        verify(accessor, never()).getTimeDomain();
    }

    @Test
    public void testTemporalDomainContinuousIntervalDoesNotFallBackToFullDomainOnNullMinTime() throws Exception {
        ReaderDimensionsAccessor accessor = mock(ReaderDimensionsAccessor.class);
        // simulates the reader/store failing to compute the min/max extrema
        when(accessor.getMinTime()).thenReturn(null);

        DimensionInfo timeInfo = new DimensionInfoImpl();
        timeInfo.setPresentation(DimensionPresentation.DISCRETE_INTERVAL);

        TreeSet<Object> domain = new TemporalDimensionRasterHelper(timeInfo, accessor).getDomain();

        assertTrue(domain.isEmpty());
        verify(accessor, never()).getTimeDomain();
    }

    @Test
    public void testElevationDomainListFetchesFullDomain() throws Exception {
        ReaderDimensionsAccessor accessor = mock(ReaderDimensionsAccessor.class);
        TreeSet<Object> fullDomain = new TreeSet<>(List.of(0d, 50d, 100d));
        when(accessor.getElevationDomain()).thenReturn(fullDomain);

        DimensionInfo elevationInfo = new DimensionInfoImpl();
        elevationInfo.setPresentation(DimensionPresentation.LIST);

        TreeSet<Object> domain = new ElevationDimensionRasterHelper(elevationInfo, accessor).getDomain();

        assertEquals(fullDomain, domain);
        verify(accessor, never()).getMinElevation();
    }

    @Test
    public void testElevationDomainDiscreteIntervalDoesNotFallBackToFullDomainOnNullMinElevation() throws Exception {
        ReaderDimensionsAccessor accessor = mock(ReaderDimensionsAccessor.class);
        // simulates the reader/store failing to compute the min/max extrema
        when(accessor.getMinElevation()).thenReturn(null);

        DimensionInfo elevationInfo = new DimensionInfoImpl();
        elevationInfo.setPresentation(DimensionPresentation.DISCRETE_INTERVAL);

        TreeSet<Object> domain = new ElevationDimensionRasterHelper(elevationInfo, accessor).getDomain();

        assertTrue(domain.isEmpty());
        verify(accessor, never()).getElevationDomain();
    }

    @Test
    public void testTemporalDomainRepresentationHandlesEmptyDomain() {
        DimensionInfo timeInfo = new DimensionInfoImpl();
        TreeSet<Object> empty = new TreeSet<>();

        timeInfo.setPresentation(DimensionPresentation.CONTINUOUS_INTERVAL);
        assertEquals(null, DimensionHelper.getTemporalDomainRepresentation(timeInfo, empty));

        timeInfo.setPresentation(DimensionPresentation.DISCRETE_INTERVAL);
        assertEquals(null, DimensionHelper.getTemporalDomainRepresentation(timeInfo, empty));
    }

    @Test
    public void testNumberRepresentationHandlesEmptyDomain() {
        DimensionInfo elevationInfo = new DimensionInfoImpl();
        TreeSet<Object> empty = new TreeSet<>();

        elevationInfo.setPresentation(DimensionPresentation.CONTINUOUS_INTERVAL);
        assertEquals(null, DimensionHelper.getNumberRepresentation(elevationInfo, empty));

        elevationInfo.setPresentation(DimensionPresentation.DISCRETE_INTERVAL);
        assertEquals(null, DimensionHelper.getNumberRepresentation(elevationInfo, empty));
    }
}
