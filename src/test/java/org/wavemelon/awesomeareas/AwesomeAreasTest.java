package org.wavemelon.awesomeareas;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wavemelon.awesomeareas.core.area.Area;
import org.wavemelon.awesomeareas.core.area.AreaType;
import org.wavemelon.awesomeareas.core.boundary.BlockGridBoundary;
import org.wavemelon.awesomeareas.core.boundary.Boundary;
import org.wavemelon.awesomeareas.core.boundary.BoxBoundary;
import org.wavemelon.awesomeareas.core.boundary.PolygonBoundary;
import org.wavemelon.awesomeareas.core.hierarchy.HierarchyValidator;
import org.wavemelon.awesomeareas.core.history.AreaHistoryEntry;
import org.wavemelon.awesomeareas.core.history.GameCalendar;
import org.wavemelon.awesomeareas.core.home.Home;
import org.wavemelon.awesomeareas.core.storage.AreaManager;

import java.util.List;
import java.util.UUID;

public class AwesomeAreasTest {

    @Test
    public void testAreaCreationAndNaming() {
        // Counties must enforce "County" suffix
        String formattedCounty = AreaType.COUNTY.formatAreaName("Grant", null);
        Assertions.assertEquals("Grant County", formattedCounty);

        String alreadyCounty = AreaType.COUNTY.formatAreaName("Grant County", null);
        Assertions.assertEquals("Grant County", alreadyCounty);

        // Standalone checks
        Assertions.assertTrue(AreaType.COUNTRY.canExistStandalone());
        Assertions.assertTrue(AreaType.TRIBE.canExistStandalone());
        Assertions.assertFalse(AreaType.PROVINCE.canExistStandalone());
        Assertions.assertFalse(AreaType.COUNTY.canExistStandalone());
    }

    @Test
    public void testHierarchyValidation() {
        AreaManager manager = new AreaManager();

        // 1. Root country
        Area country = new Area(UUID.randomUUID(), "Arstotzka", AreaType.COUNTRY, new BoxBoundary(-1000, -1000, 1000, 1000));
        HierarchyValidator.ValidationResult r1 = manager.addArea(country);
        Assertions.assertTrue(r1.valid(), "Country should be successfully added");

        // 2. Province inside country
        Area province = new Area(UUID.randomUUID(), "Grestin Province", AreaType.PROVINCE, new BoxBoundary(-500, -500, 500, 500));
        province.setParentId(country.getId());
        HierarchyValidator.ValidationResult r2 = manager.addArea(province);
        Assertions.assertTrue(r2.valid(), "Province inside country should be valid");

        // 3. County directly under Country without Province (Invalid hierarchy)
        Area invalidCounty = new Area(UUID.randomUUID(), "Orvech County", AreaType.COUNTY, new BoxBoundary(0, 0, 100, 100));
        invalidCounty.setParentId(country.getId());
        HierarchyValidator.ValidationResult r3 = manager.addArea(invalidCounty);
        Assertions.assertFalse(r3.valid(), "County directly under Country should be rejected");

        // 4. Standalone province (Invalid - cannot exist standalone)
        Area rogueProvince = new Area(UUID.randomUUID(), "Rogue Province", AreaType.PROVINCE, new BoxBoundary(2000, 2000, 3000, 3000));
        HierarchyValidator.ValidationResult r4 = manager.addArea(rogueProvince);
        Assertions.assertFalse(r4.valid(), "Province cannot exist without a parent");
    }

    @Test
    public void testSubdivisionAndAncestry() {
        AreaManager manager = new AreaManager();

        Area country = new Area(UUID.randomUUID(), "United Lands", AreaType.COUNTRY, new BoxBoundary(0, 0, 1000, 1000));
        manager.addArea(country);

        Area province = new Area(UUID.randomUUID(), "North Province", AreaType.PROVINCE, new BoxBoundary(100, 100, 900, 900));
        province.setParentId(country.getId());
        manager.addArea(province);

        Area county = new Area(UUID.randomUUID(), "Highland County", AreaType.COUNTY, new BoxBoundary(200, 200, 800, 800));
        county.setParentId(province.getId());
        manager.addArea(county);

        Area city = new Area(UUID.randomUUID(), "Highpoint City", AreaType.CITY, new BoxBoundary(300, 300, 400, 400));
        city.setParentId(county.getId());
        manager.addArea(city);

        List<Area> subs = manager.getSubAreas(country.getId());
        Assertions.assertEquals(1, subs.size());
        Assertions.assertEquals(province.getId(), subs.get(0).getId());

        Area innermost = manager.getInnermostAreaAt(350, 350);
        Assertions.assertNotNull(innermost);
        Assertions.assertEquals(city.getId(), innermost.getId());
    }

    @Test
    public void testBoxBoundaryIntersection() {
        BoxBoundary box1 = new BoxBoundary(0, 0, 100, 100);
        BoxBoundary box2 = new BoxBoundary(50, 50, 150, 150);
        BoxBoundary box3 = new BoxBoundary(200, 200, 300, 300);

        Assertions.assertTrue(box1.intersects(box2));
        Assertions.assertTrue(box2.intersects(box1));
        Assertions.assertFalse(box1.intersects(box3));

        Assertions.assertTrue(box1.contains(50, 50));
        Assertions.assertFalse(box1.contains(150, 150));
    }

    @Test
    public void testPopulationCount() {
        AreaManager manager = new AreaManager();

        Area country = new Area(UUID.randomUUID(), "Kingdom of Ooo", AreaType.COUNTRY, new BoxBoundary(-500, -500, 500, 500));
        manager.addArea(country);

        Area city = new Area(UUID.randomUUID(), "Candy Kingdom City", AreaType.CITY, new BoxBoundary(0, 0, 100, 100));
        city.setParentId(country.getId());
        city.setVillagerCount(12);
        manager.addArea(city);

        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        manager.setHome(new Home(UUID.randomUUID(), player1, "Finn", 50, 64, 50, "minecraft:overworld", false));
        manager.setHome(new Home(UUID.randomUUID(), player2, "Jake", 60, 64, 60, "minecraft:overworld", false));

        // Total pop in city = 2 players + 12 villagers = 14
        Assertions.assertEquals(14, manager.getPopulation(city.getId()));
        // Total pop in country includes city = 14
        Assertions.assertEquals(14, manager.getPopulation(country.getId()));
    }

    @Test
    public void testAreaRenameAndHistory() {
        AreaManager manager = new AreaManager();

        Area area = new Area(UUID.randomUUID(), "Oldtown", AreaType.CITY, new BoxBoundary(0, 0, 50, 50));
        manager.addArea(area);

        boolean renamed = manager.renameArea(area.getId(), "Newtown", "Admin", "Year 1, Spring 12");
        Assertions.assertTrue(renamed);
        Assertions.assertEquals("Newtown", area.getName());
        Assertions.assertTrue(area.getPastNames().contains("Oldtown"));

        List<AreaHistoryEntry> history = area.getHistory();
        Assertions.assertFalse(history.isEmpty());
        AreaHistoryEntry lastEntry = history.get(history.size() - 1);
        Assertions.assertEquals("RENAMED", lastEntry.getEventType());
        Assertions.assertEquals("Admin", lastEntry.getActorName());
    }

    @Test
    public void testCalendarDateFormatting() {
        String date1 = GameCalendar.formatDate(0);
        Assertions.assertEquals("January 1, Year 1", date1);

        // 24000 ticks per day. Day 31 is February 1.
        long ticksFeb = 31L * 24000L;
        String date2 = GameCalendar.formatDate(ticksFeb);
        Assertions.assertEquals("February 1, Year 1", date2);

        // 365 days per year
        long ticksYear2 = 365L * 24000L;
        String date3 = GameCalendar.formatDate(ticksYear2);
        Assertions.assertEquals("January 1, Year 2", date3);
    }

    @Test
    public void testBorderVisualizerSeams() {
        BoxBoundary box = new BoxBoundary(10, 20, 30, 40);
        List<Boundary.BorderSegment> segments = box.getBorderSegments();

        Assertions.assertEquals(4, segments.size());
        // Verify segments are placed at integer coordinates along block edges
        for (Boundary.BorderSegment s : segments) {
            Assertions.assertTrue(s.isHorizontal() || s.isVertical());
        }
    }

    @Test
    public void testPeerAreaOverlaps() {
        AreaManager manager = new AreaManager();

        Area cityA = new Area(UUID.randomUUID(), "City Alpha", AreaType.CITY, new BoxBoundary(0, 0, 50, 50));
        Area cityB = new Area(UUID.randomUUID(), "City Beta", AreaType.CITY, new BoxBoundary(100, 100, 150, 150));
        Area cityC = new Area(UUID.randomUUID(), "City Gamma", AreaType.CITY, new BoxBoundary(25, 25, 75, 75)); // overlaps cityA

        HierarchyValidator.ValidationResult r1 = manager.addArea(cityA);
        Assertions.assertTrue(r1.valid());

        HierarchyValidator.ValidationResult r2 = manager.addArea(cityB);
        Assertions.assertTrue(r2.valid());

        HierarchyValidator.ValidationResult r3 = manager.addArea(cityC);
        Assertions.assertFalse(r3.valid(), "Peer areas sharing blocks must be rejected due to overlap");
    }

    @Test
    public void testBlockGridBoundaryFromPolygon() {
        // Create an L-shaped polygon and convert to block grid
        List<PolygonBoundary.Point2D> points = List.of(
                new PolygonBoundary.Point2D(0, 0),
                new PolygonBoundary.Point2D(20, 0),
                new PolygonBoundary.Point2D(20, 10),
                new PolygonBoundary.Point2D(10, 10),
                new PolygonBoundary.Point2D(10, 20),
                new PolygonBoundary.Point2D(0, 20)
        );

        BlockGridBoundary boundary = BlockGridBoundary.fromPolygon(points);
        Assertions.assertTrue(boundary.contains(5, 5));
        Assertions.assertTrue(boundary.contains(15, 5));
        Assertions.assertTrue(boundary.contains(5, 15));
        Assertions.assertFalse(boundary.contains(15, 15)); // The cutout of the L

        // Check border segments align to integer seams
        for (Boundary.BorderSegment seg : boundary.getBorderSegments()) {
            Assertions.assertTrue(seg.isHorizontal() || seg.isVertical(), "Seam border segments must be axis-aligned block edges");
        }
    }

    @Test
    public void testJsonExportAndImport() {
        AreaManager manager = new AreaManager();

        Area country = new Area(UUID.randomUUID(), "Sun Realm", AreaType.COUNTRY, new BoxBoundary(-500, -500, 500, 500));
        manager.addArea(country);

        Home home = new Home(UUID.randomUUID(), UUID.randomUUID(), "Alice", 10, 70, 20, "minecraft:overworld", false);
        manager.setHome(home);

        String json = manager.exportToJsonString();
        Assertions.assertNotNull(json);

        AreaManager restored = new AreaManager();
        restored.loadFromJsonString(json);

        Assertions.assertEquals(1, restored.getAllAreas().size());
        Area restoredCountry = restored.getAreaByName("Sun Realm");
        Assertions.assertNotNull(restoredCountry);
        Assertions.assertEquals(AreaType.COUNTRY, restoredCountry.getType());

        Assertions.assertEquals(1, restored.getAllHomes().size());
        Assertions.assertEquals(1, restored.getPopulation(restoredCountry.getId()));
    }

    @Test
    public void testBorderSnappingToParent() {
        AreaManager manager = new AreaManager();

        // Country boundary: [0, 0] to [100, 100]
        Area country = new Area(UUID.randomUUID(), "Empire", AreaType.COUNTRY, new BoxBoundary(0, 0, 100, 100));
        manager.addArea(country);

        // Province boundary misstep: drawn from [20, 20] to [120, 80] (overshooting the eastern country border by 20 blocks!)
        Area province = new Area(UUID.randomUUID(), "East Province", AreaType.PROVINCE, new BoxBoundary(20, 20, 120, 80));
        province.setParentId(country.getId());

        // Without snapping, this would fail because [101..120] is outside country.
        // With automatic snapping, it clips to [20, 20, 100, 80] and succeeds!
        HierarchyValidator.ValidationResult result = manager.addArea(province);
        Assertions.assertTrue(result.valid(), "Border misstep should automatically snap to inside of parent");

        Boundary snapped = province.getBoundary();
        Assertions.assertNotNull(snapped);
        Assertions.assertEquals(20, snapped.getMinX());
        Assertions.assertEquals(100, snapped.getMaxX(), "Eastern edge must snap to parent maxX (100)");
        Assertions.assertEquals(20, snapped.getMinZ());
        Assertions.assertEquals(80, snapped.getMaxZ());
        Assertions.assertTrue(country.getBoundary().contains(snapped), "Snapped boundary must be fully contained in parent");
    }

    @Test
    public void testClearManagerOnWorldSwitch() {
        AreaManager manager = new AreaManager();
        Area country = new Area(UUID.randomUUID(), "OldWorldCountry", AreaType.COUNTRY, new BoxBoundary(0, 0, 100, 100));
        manager.addArea(country);
        Assertions.assertEquals(1, manager.getAllAreas().size());

        // Clear when switching worlds
        manager.clear();
        Assertions.assertEquals(0, manager.getAllAreas().size());
        Assertions.assertNull(manager.getAreaByName("OldWorldCountry"));
    }
}
