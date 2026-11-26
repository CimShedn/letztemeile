package com.example.data.staticdata

import com.example.model.GeoPoint
import com.example.model.MunichDistrict
import com.example.model.StationType
import com.example.model.TransitStation
import org.json.JSONArray
import org.json.JSONObject

/**
 * Instant zero-latency transit base layer for Greater Munich / Bavaria (S-Bahn S1–S8/S20,
 * Munich Bus trunk corridors, and Regional RE/RB + Fernverkehr ICE/IC corridors).
 * Outside or zoomed into any region worldwide/DACH, OverpassTransitRepository dynamically
 * augments the map with live OpenStreetMap transit routes & official OSM `colour` tags.
 */
object MunichSBahnNetwork {

    val stations: List<TransitStation> = listOf(
        // Stammstrecke (West -> Ost)
        TransitStation("8004151", "Pasing", GeoPoint(48.1499, 11.4616), StationType.SBAHN, listOf("S3", "S4", "S6", "S8", "S20"), "Pasing"),
        TransitStation("8004133", "Laim", GeoPoint(48.1444, 11.5034), StationType.SBAHN, listOf("S1", "S2", "S3", "S4", "S6", "S8"), "Laim"),
        TransitStation("8004134", "Hirschgarten", GeoPoint(48.1434, 11.5191), StationType.SBAHN, listOf("S1", "S2", "S3", "S4", "S6", "S8"), "Neuhausen"),
        TransitStation("8004128", "Donnersbergerbrücke", GeoPoint(48.1426, 11.5364), StationType.SBAHN, listOf("S1", "S2", "S3", "S4", "S6", "S7", "S8"), "Neuhausen"),
        TransitStation("8004129", "Hackerbrücke", GeoPoint(48.1419, 11.5484), StationType.SBAHN, listOf("S1", "S2", "S3", "S4", "S6", "S7", "S8"), "Maxvorstadt"),
        TransitStation("8000261", "München Hbf", GeoPoint(48.1402, 11.5599), StationType.SBAHN, listOf("S1", "S2", "S3", "S4", "S6", "S7", "S8"), "Ludwigsvorstadt"),
        TransitStation("8004139", "Karlsplatz (Stachus)", GeoPoint(48.1392, 11.5660), StationType.SBAHN, listOf("S1", "S2", "S3", "S4", "S6", "S8"), "Altstadt"),
        TransitStation("8004135", "Marienplatz", GeoPoint(48.1372, 11.5754), StationType.SBAHN, listOf("S1", "S2", "S3", "S4", "S6", "S8"), "Altstadt"),
        TransitStation("8004132", "Isartor", GeoPoint(48.1348, 11.5829), StationType.SBAHN, listOf("S1", "S2", "S3", "S4", "S6", "S8"), "Altstadt"),
        TransitStation("8004136", "Rosenheimer Platz", GeoPoint(48.1286, 11.5941), StationType.SBAHN, listOf("S1", "S2", "S3", "S4", "S6", "S8"), "Haidhausen"),
        TransitStation("8000262", "München Ost", GeoPoint(48.1272, 11.6048), StationType.SBAHN, listOf("S1", "S2", "S3", "S4", "S6", "S8"), "Haidhausen"),
        TransitStation("8004168", "Leuchtenbergring", GeoPoint(48.1344, 11.6156), StationType.SBAHN, listOf("S1", "S2", "S4", "S6", "S8"), "Berg am Laim"),

        // S1 Nord
        TransitStation("8004126", "Moosach", GeoPoint(48.1802, 11.5067), StationType.SBAHN, listOf("S1"), "Moosach"),
        TransitStation("8004160", "Fasanerie", GeoPoint(48.1984, 11.5290), StationType.SBAHN, listOf("S1"), "Feldmoching"),
        TransitStation("8004130", "Feldmoching", GeoPoint(48.2138, 11.5416), StationType.SBAHN, listOf("S1"), "Feldmoching"),
        TransitStation("8004590", "Oberschleißheim", GeoPoint(48.2546, 11.5594), StationType.SBAHN, listOf("S1"), "Oberschleißheim"),
        TransitStation("8003736", "Lohhof", GeoPoint(48.2730, 11.5806), StationType.SBAHN, listOf("S1"), "Unterschleißheim"),
        TransitStation("8005989", "Unterschleißheim", GeoPoint(48.2820, 11.5924), StationType.SBAHN, listOf("S1"), "Unterschleißheim"),
        TransitStation("8001645", "Eching", GeoPoint(48.3005, 11.6186), StationType.SBAHN, listOf("S1"), "Eching"),
        TransitStation("8004260", "Neufahrn (b Freising)", GeoPoint(48.3210, 11.6606), StationType.SBAHN, listOf("S1"), "Neufahrn"),
        TransitStation("8004904", "Pulling", GeoPoint(48.3627, 11.7160), StationType.SBAHN, listOf("S1"), "Freising"),
        TransitStation("8002080", "Freising", GeoPoint(48.3958, 11.7453), StationType.SBAHN, listOf("S1"), "Freising"),
        TransitStation("8004167", "Flughafen Besucherpark", GeoPoint(48.3522, 11.7589), StationType.SBAHN, listOf("S1", "S8"), "Flughafen"),
        TransitStation("8004166", "Flughafen München", GeoPoint(48.3538, 11.7861), StationType.SBAHN, listOf("S1", "S8"), "Flughafen"),

        // S2 Nordwest
        TransitStation("8004138", "Obermenzing", GeoPoint(48.1651, 11.4730), StationType.SBAHN, listOf("S2"), "Obermenzing"),
        TransitStation("8004140", "Untermenzing", GeoPoint(48.1764, 11.4728), StationType.SBAHN, listOf("S2"), "Untermenzing"),
        TransitStation("8004127", "Allach", GeoPoint(48.1916, 11.4678), StationType.SBAHN, listOf("S2"), "Allach"),
        TransitStation("8003190", "Karlsfeld", GeoPoint(48.2116, 11.4609), StationType.SBAHN, listOf("S2"), "Karlsfeld"),
        TransitStation("8001342", "Dachau Bahnhof", GeoPoint(48.2546, 11.4443), StationType.SBAHN, listOf("S2"), "Dachau"),
        TransitStation("8004781", "Petershausen", GeoPoint(48.4106, 11.4706), StationType.SBAHN, listOf("S2"), "Petershausen"),

        // S2 Ost
        TransitStation("8004125", "Berg am Laim", GeoPoint(48.1337, 11.6335), StationType.SBAHN, listOf("S2", "S4", "S6"), "Berg am Laim"),
        TransitStation("8004165", "Riem", GeoPoint(48.1445, 11.6787), StationType.SBAHN, listOf("S2"), "Riem"),
        TransitStation("8001972", "Feldkirchen (b München)", GeoPoint(48.1496, 11.7330), StationType.SBAHN, listOf("S2"), "Feldkirchen"),
        TransitStation("8002708", "Heimstetten", GeoPoint(48.1619, 11.7616), StationType.SBAHN, listOf("S2"), "Kirchheim"),
        TransitStation("8002437", "Grub (Oberbay)", GeoPoint(48.1705, 11.7875), StationType.SBAHN, listOf("S2"), "Poing"),
        TransitStation("8004844", "Poing", GeoPoint(48.1726, 11.8117), StationType.SBAHN, listOf("S2"), "Poing"),
        TransitStation("8003883", "Markt Schwaben", GeoPoint(48.1912, 11.8602), StationType.SBAHN, listOf("S2"), "Markt Schwaben"),
        TransitStation("8001815", "Erding", GeoPoint(48.3027, 11.9082), StationType.SBAHN, listOf("S2"), "Erding"),

        // S3 West
        TransitStation("8004131", "Langwied", GeoPoint(48.1642, 11.4282), StationType.SBAHN, listOf("S3"), "Aubing"),
        TransitStation("8004142", "Lochhausen", GeoPoint(48.1743, 11.4083), StationType.SBAHN, listOf("S3"), "Lochhausen"),
        TransitStation("8002396", "Gröbenzell", GeoPoint(48.1932, 11.3738), StationType.SBAHN, listOf("S3"), "Gröbenzell"),
        TransitStation("8004651", "Olching", GeoPoint(48.2091, 11.3358), StationType.SBAHN, listOf("S3"), "Olching"),
        TransitStation("8003846", "Maisach", GeoPoint(48.2169, 11.2610), StationType.SBAHN, listOf("S3"), "Maisach"),
        TransitStation("8003852", "Mammendorf", GeoPoint(48.2063, 11.1647), StationType.SBAHN, listOf("S3"), "Mammendorf"),

        // S3 Südost
        TransitStation("8004163", "St.-Martin-Straße", GeoPoint(48.1174, 11.5999), StationType.SBAHN, listOf("S3"), "Giesing"),
        TransitStation("8004162", "Giesing", GeoPoint(48.1109, 11.5956), StationType.SBAHN, listOf("S3"), "Giesing"),
        TransitStation("8004164", "Fasangarten", GeoPoint(48.0977, 11.6015), StationType.SBAHN, listOf("S3"), "Fasangarten"),
        TransitStation("8004170", "Fasanenpark", GeoPoint(48.0816, 11.6085), StationType.SBAHN, listOf("S3"), "Unterhaching"),
        TransitStation("8005975", "Unterhaching", GeoPoint(48.0672, 11.6130), StationType.SBAHN, listOf("S3"), "Unterhaching"),
        TransitStation("8005801", "Taufkirchen", GeoPoint(48.0468, 11.6184), StationType.SBAHN, listOf("S3"), "Taufkirchen"),
        TransitStation("8002167", "Furth (b Deisenhofen)", GeoPoint(48.0341, 11.6061), StationType.SBAHN, listOf("S3"), "Oberhaching"),
        TransitStation("8001390", "Deisenhofen", GeoPoint(48.0218, 11.5881), StationType.SBAHN, listOf("S3"), "Oberhaching"),
        TransitStation("8000174", "Holzkirchen", GeoPoint(47.8825, 11.6960), StationType.SBAHN, listOf("S3"), "Holzkirchen"),

        // S4 West
        TransitStation("8004154", "Aubing", GeoPoint(48.1558, 11.4139), StationType.SBAHN, listOf("S4"), "Aubing"),
        TransitStation("8004901", "Puchheim", GeoPoint(48.1656, 11.3563), StationType.SBAHN, listOf("S4"), "Puchheim"),
        TransitStation("8001715", "Eichenau (Oberbay)", GeoPoint(48.1684, 11.3189), StationType.SBAHN, listOf("S4"), "Eichenau"),
        TransitStation("8002153", "Fürstenfeldbruck", GeoPoint(48.1704, 11.2584), StationType.SBAHN, listOf("S4"), "Fürstenfeldbruck"),
        TransitStation("8000121", "Geltendorf", GeoPoint(48.1059, 11.0342), StationType.SBAHN, listOf("S4"), "Geltendorf"),

        // S4 / S6 Ost
        TransitStation("8004169", "Trudering", GeoPoint(48.1257, 11.6634), StationType.SBAHN, listOf("S4", "S6"), "Trudering"),
        TransitStation("8002412", "Gronsdorf", GeoPoint(48.1173, 11.7037), StationType.SBAHN, listOf("S4", "S6"), "Haar"),
        TransitStation("8002492", "Haar", GeoPoint(48.1088, 11.7305), StationType.SBAHN, listOf("S4", "S6"), "Haar"),
        TransitStation("8006055", "Vaterstetten", GeoPoint(48.1057, 11.7717), StationType.SBAHN, listOf("S4", "S6"), "Vaterstetten"),
        TransitStation("8000797", "Baldham", GeoPoint(48.0977, 11.7878), StationType.SBAHN, listOf("S4", "S6"), "Vaterstetten"),
        TransitStation("8006684", "Zorneding", GeoPoint(48.0848, 11.8240), StationType.SBAHN, listOf("S4", "S6"), "Zorneding"),
        TransitStation("8000130", "Grafing Bahnhof", GeoPoint(48.0439, 11.9438), StationType.SBAHN, listOf("S4", "S6"), "Grafing"),
        TransitStation("8001638", "Ebersberg (Oberbay)", GeoPoint(48.0764, 11.9718), StationType.SBAHN, listOf("S4", "S6"), "Ebersberg"),

        // S6 West
        TransitStation("8004153", "Westkreuz", GeoPoint(48.1489, 11.4411), StationType.SBAHN, listOf("S6", "S8"), "Pasing"),
        TransitStation("8003728", "Lochham", GeoPoint(48.1292, 11.4342), StationType.SBAHN, listOf("S6"), "Gräfelfing"),
        TransitStation("8002371", "Gräfelfing", GeoPoint(48.1178, 11.4307), StationType.SBAHN, listOf("S6"), "Gräfelfing"),
        TransitStation("8004823", "Planegg", GeoPoint(48.1054, 11.4253), StationType.SBAHN, listOf("S6"), "Planegg"),
        TransitStation("8002227", "Gauting", GeoPoint(48.0672, 11.3792), StationType.SBAHN, listOf("S6"), "Gauting"),
        TransitStation("8005681", "Starnberg", GeoPoint(47.9958, 11.3436), StationType.SBAHN, listOf("S6"), "Starnberg"),
        TransitStation("8000155", "Tutzing", GeoPoint(47.9087, 11.2747), StationType.SBAHN, listOf("S6"), "Tutzing"),

        // S7 Süd
        TransitStation("8004137", "Heimeranplatz", GeoPoint(48.1333, 11.5335), StationType.SBAHN, listOf("S7", "S20"), "Sendling-Westpark"),
        TransitStation("8004155", "Harras", GeoPoint(48.1173, 11.5369), StationType.SBAHN, listOf("S7"), "Sendling"),
        TransitStation("8004156", "Mittersendling", GeoPoint(48.1079, 11.5361), StationType.SBAHN, listOf("S7", "S20"), "Sendling"),
        TransitStation("8004157", "Siemenswerke", GeoPoint(48.0942, 11.5334), StationType.SBAHN, listOf("S7", "S20"), "Obersendling"),
        TransitStation("8004159", "Solln", GeoPoint(48.0785, 11.5256), StationType.SBAHN, listOf("S7", "S20"), "Solln"),
        TransitStation("8002427", "Großhesselohe Isartalbf", GeoPoint(48.0650, 11.5228), StationType.SBAHN, listOf("S7", "S20"), "Pullach"),
        TransitStation("8004903", "Pullach", GeoPoint(48.0558, 11.5193), StationType.SBAHN, listOf("S7"), "Pullach"),
        TransitStation("8002899", "Höllriegelskreuth", GeoPoint(48.0468, 11.5137), StationType.SBAHN, listOf("S7", "S20"), "Pullach"),
        TransitStation("8006560", "Wolfratshausen", GeoPoint(47.9136, 11.4258), StationType.SBAHN, listOf("S7"), "Wolfratshausen"),

        // S8 West
        TransitStation("8004152", "Neuaubing", GeoPoint(48.1404, 11.4193), StationType.SBAHN, listOf("S8"), "Aubing"),
        TransitStation("8004173", "Freiham", GeoPoint(48.1365, 11.4017), StationType.SBAHN, listOf("S8"), "Freiham"),
        TransitStation("8002323", "Germering-Unterpfaffenhofen", GeoPoint(48.1304, 11.3679), StationType.SBAHN, listOf("S8"), "Germering"),
        TransitStation("8002293", "Gilching-Argelsried", GeoPoint(48.1097, 11.3005), StationType.SBAHN, listOf("S8"), "Gilching"),
        TransitStation("8006379", "Weßling (Oberbay)", GeoPoint(48.0764, 11.2498), StationType.SBAHN, listOf("S8"), "Weßling"),
        TransitStation("8002776", "Herrsching", GeoPoint(47.9993, 11.1749), StationType.SBAHN, listOf("S8"), "Herrsching"),

        // S8 Ost
        TransitStation("8004145", "Daglfing", GeoPoint(48.1510, 11.6499), StationType.SBAHN, listOf("S8"), "Bogenhausen"),
        TransitStation("8004146", "Englschalking", GeoPoint(48.1583, 11.6487), StationType.SBAHN, listOf("S8"), "Bogenhausen"),
        TransitStation("8004147", "Johanneskirchen", GeoPoint(48.1704, 11.6458), StationType.SBAHN, listOf("S8"), "Bogenhausen"),
        TransitStation("8005971", "Unterföhring", GeoPoint(48.1912, 11.6455), StationType.SBAHN, listOf("S8"), "Unterföhring"),
        TransitStation("8003084", "Ismaning", GeoPoint(48.2256, 11.6744), StationType.SBAHN, listOf("S8"), "Ismaning"),
        TransitStation("8002546", "Hallbergmoos", GeoPoint(48.3137, 11.7145), StationType.SBAHN, listOf("S8"), "Hallbergmoos")
    )

    val stationsById: Map<String, TransitStation> = stations.associateBy { it.id }

    private data class ColoredPart(val label: String, val colorHex: String)

    private data class OffsetSegment(
        val line: String,
        val colorHex: String,
        val offsetIndex: Double,
        val stationIds: List<String>
    )

    private data class MultiColorLabelSegment(
        val parts: List<ColoredPart>,
        val stationIds: List<String>
    )

    private data class IntegratedTerminalBadge(
        val stationId: String,
        val stationName: String,
        val label: String,
        val colorHex: String,
        val textColorHex: String,
        val location: GeoPoint,
        val offsetLon: Double = 0.0
    )

    private val stammstrecke = listOf(
        "8004133", "8004134", "8004128", "8004129", "8000261",
        "8004139", "8004135", "8004132", "8004136", "8000262"
    )

    data class StaticLineOrder(
        val name: String,
        val stationIds: List<String>
    )

    val sBahnLineOrders: List<StaticLineOrder> = listOf(
        StaticLineOrder(
            "S1",
            listOf("8002080", "8004904", "8004260", "8001645", "8005989", "8003736", "8004590", "8004130", "8004160", "8004126") +
                stammstrecke + listOf("8004168")
        ),
        StaticLineOrder(
            "S1",
            listOf("8004166", "8004167", "8004260", "8001645", "8005989", "8003736", "8004590", "8004130", "8004160", "8004126") +
                stammstrecke + listOf("8004168")
        ),
        StaticLineOrder(
            "S2",
            listOf("8004781", "8001342", "8003190", "8004127", "8004140", "8004138") +
                stammstrecke +
                listOf("8004168", "8004125", "8004165", "8001972", "8002708", "8002437", "8004844", "8003883", "8001815")
        ),
        StaticLineOrder(
            "S3",
            listOf("8003852", "8003846", "8004651", "8002396", "8004142", "8004131", "8004151") +
                stammstrecke +
                listOf("8004163", "8004162", "8004164", "8004170", "8005975", "8005801", "8002167", "8001390", "8000174")
        ),
        StaticLineOrder(
            "S4",
            listOf("8000121", "8002153", "8001715", "8004901", "8004154", "8004151") +
                stammstrecke +
                listOf("8004168", "8004125", "8004169", "8002412", "8002492", "8006055", "8000797", "8006684", "8000130", "8001638")
        ),
        StaticLineOrder(
            "S6",
            listOf("8000155", "8005681", "8002227", "8004823", "8002371", "8003728", "8004153", "8004151") +
                stammstrecke +
                listOf("8004168", "8004125", "8004169", "8002412", "8002492", "8006055", "8000797", "8006684", "8000130", "8001638")
        ),
        StaticLineOrder(
            "S7",
            listOf("8000261", "8004129", "8004128", "8004137", "8004155", "8004156", "8004157", "8004159", "8002427", "8004903", "8002899", "8006560")
        ),
        StaticLineOrder(
            "S8",
            listOf("8002776", "8006379", "8002293", "8002323", "8004173", "8004152", "8004153", "8004151") +
                stammstrecke +
                listOf("8004168", "8004145", "8004146", "8004147", "8005971", "8003084", "8002546", "8004167", "8004166")
        ),
        StaticLineOrder(
            "S20",
            listOf("8004151", "8004137", "8004156", "8004157", "8004159", "8002427", "8004903", "8002899")
        )
    )

    /**
     * S-Bahn line geometries split so shared corridors run cleanly in parallel via `line-offset`.
     */
    private val sBahnOffsetSegments: List<OffsetSegment> = listOf(
        // Exclusive outer branches (offsetIndex = 0.0)
        OffsetSegment("S1", "#16BAE7", 0.0, listOf("8002080", "8004904", "8004260", "8001645", "8005989", "8003736", "8004590", "8004130", "8004160", "8004126", "8004133")),
        OffsetSegment("S1", "#16BAE7", 0.0, listOf("8004260", "8004167")),
        OffsetSegment("S2", "#76B82A", 0.0, listOf("8004781", "8001342", "8003190", "8004127", "8004140", "8004138", "8004133")),
        OffsetSegment("S2", "#76B82A", 0.0, listOf("8004125", "8004165", "8001972", "8002708", "8002437", "8004844", "8003883", "8001815")),
        OffsetSegment("S3", "#951B81", 0.0, listOf("8003852", "8003846", "8004651", "8002396", "8004142", "8004131", "8004151")),
        OffsetSegment("S3", "#951B81", 0.0, listOf("8000262", "8004163", "8004162", "8004164", "8004170", "8005975", "8005801", "8002167", "8001390", "8000174")),
        OffsetSegment("S4", "#E30613", 0.0, listOf("8000121", "8002153", "8001715", "8004901", "8004154", "8004151")),
        OffsetSegment("S6", "#00975F", 0.0, listOf("8000155", "8005681", "8002227", "8004823", "8002371", "8003728", "8004153")),
        OffsetSegment("S7", "#963833", 0.0, listOf("8000261", "8004129", "8004128", "8004137", "8004155", "8004156")),
        OffsetSegment("S7", "#963833", 0.0, listOf("8002899", "8006560")),
        OffsetSegment("S8", "#F7A600", 0.0, listOf("8002776", "8006379", "8002293", "8002323", "8004173", "8004152", "8004153")),
        OffsetSegment("S8", "#F7A600", 0.0, listOf("8004168", "8004145", "8004146", "8004147", "8005971", "8003084", "8002546", "8004167")),
        OffsetSegment("S20", "#ED6B83", 0.0, listOf("8004151", "8004137", "8004156")),

        // Shared S1 + S8 Flughafen branch (parallel!)
        OffsetSegment("S1", "#16BAE7", -0.55, listOf("8004167", "8004166")),
        OffsetSegment("S8", "#F7A600", 0.55, listOf("8004167", "8004166")),

        // Shared S4 + S6 East (Berg am Laim -> Ebersberg, parallel!)
        OffsetSegment("S4", "#E30613", -0.55, listOf("8004125", "8004169", "8002412", "8002492", "8006055", "8000797", "8006684", "8000130", "8001638")),
        OffsetSegment("S6", "#00975F", 0.55, listOf("8004125", "8004169", "8002412", "8002492", "8006055", "8000797", "8006684", "8000130", "8001638")),

        // Shared S6 + S8 Westkreuz -> Pasing (parallel!)
        OffsetSegment("S6", "#00975F", -0.55, listOf("8004153", "8004151")),
        OffsetSegment("S8", "#F7A600", 0.55, listOf("8004153", "8004151")),

        // Shared S7 + S20 Mittersendling -> Höllriegelskreuth (parallel!)
        OffsetSegment("S7", "#963833", -0.55, listOf("8004156", "8004157", "8004159", "8002427", "8004903", "8002899")),
        OffsetSegment("S20", "#ED6B83", 0.55, listOf("8004156", "8004157", "8004159", "8002427", "8004903", "8002899")),

        // Shared Pasing -> Laim (S3, S4, S6, S8 parallel!)
        OffsetSegment("S3", "#951B81", -1.5, listOf("8004151", "8004133")),
        OffsetSegment("S4", "#E30613", -0.5, listOf("8004151", "8004133")),
        OffsetSegment("S6", "#00975F", 0.5, listOf("8004151", "8004133")),
        OffsetSegment("S8", "#F7A600", 1.5, listOf("8004151", "8004133")),

        // Shared Stammstrecke Laim -> München Ost (6 parallel ribbons in official colors!)
        OffsetSegment("S1", "#16BAE7", -2.5, stammstrecke),
        OffsetSegment("S2", "#76B82A", -1.5, stammstrecke),
        OffsetSegment("S3", "#951B81", -0.5, stammstrecke),
        OffsetSegment("S4", "#E30613", 0.5, stammstrecke),
        OffsetSegment("S6", "#00975F", 1.5, stammstrecke),
        OffsetSegment("S8", "#F7A600", 2.5, stammstrecke),

        // Shared München Ost -> Leuchtenbergring -> Berg am Laim (S2, S4, S6, S8)
        OffsetSegment("S1", "#16BAE7", -1.5, listOf("8000262", "8004168")),
        OffsetSegment("S2", "#76B82A", -0.5, listOf("8000262", "8004168", "8004125")),
        OffsetSegment("S4", "#E30613", 0.5, listOf("8000262", "8004168", "8004125")),
        OffsetSegment("S6", "#00975F", 1.5, listOf("8000262", "8004168", "8004125")),
        OffsetSegment("S8", "#F7A600", -2.0, listOf("8000262", "8004168"))
    )

    /**
     * Multi-color traveling line labels: every line name carries its own official hex color!
     */
    private val sBahnMultiColorLabels: List<MultiColorLabelSegment> = listOf(
        MultiColorLabelSegment(listOf(ColoredPart("S1", "#16BAE7")), listOf("8002080", "8004904", "8004260", "8001645", "8005989", "8003736", "8004590", "8004130", "8004160", "8004126", "8004133")),
        MultiColorLabelSegment(listOf(ColoredPart("S1", "#16BAE7")), listOf("8004260", "8004167")),
        MultiColorLabelSegment(listOf(ColoredPart("S1", "#16BAE7"), ColoredPart("S8", "#D97706")), listOf("8004167", "8004166")),
        MultiColorLabelSegment(listOf(ColoredPart("S2", "#76B82A")), listOf("8004781", "8001342", "8003190", "8004127", "8004140", "8004138", "8004133")),
        MultiColorLabelSegment(listOf(ColoredPart("S2", "#76B82A")), listOf("8004125", "8004165", "8001972", "8002708", "8002437", "8004844", "8003883", "8001815")),
        MultiColorLabelSegment(listOf(ColoredPart("S3", "#951B81")), listOf("8003852", "8003846", "8004651", "8002396", "8004142", "8004131", "8004151")),
        MultiColorLabelSegment(listOf(ColoredPart("S3", "#951B81")), listOf("8000262", "8004163", "8004162", "8004164", "8004170", "8005975", "8005801", "8002167", "8001390", "8000174")),
        MultiColorLabelSegment(listOf(ColoredPart("S4", "#E30613")), listOf("8000121", "8002153", "8001715", "8004901", "8004154", "8004151")),
        MultiColorLabelSegment(listOf(ColoredPart("S4", "#E30613"), ColoredPart("S6", "#00975F")), listOf("8004125", "8004169", "8002412", "8002492", "8006055", "8000797", "8006684", "8000130", "8001638")),
        MultiColorLabelSegment(listOf(ColoredPart("S6", "#00975F")), listOf("8000155", "8005681", "8002227", "8004823", "8002371", "8003728", "8004153")),
        MultiColorLabelSegment(listOf(ColoredPart("S6", "#00975F"), ColoredPart("S8", "#D97706")), listOf("8004153", "8004151")),
        MultiColorLabelSegment(listOf(ColoredPart("S7", "#963833")), listOf("8004128", "8004137", "8004155", "8004156")),
        MultiColorLabelSegment(listOf(ColoredPart("S7", "#963833"), ColoredPart("S20", "#ED6B83")), listOf("8004156", "8004157", "8004159", "8002427", "8004903", "8002899")),
        MultiColorLabelSegment(listOf(ColoredPart("S7", "#963833")), listOf("8002899", "8006560")),
        MultiColorLabelSegment(listOf(ColoredPart("S8", "#D97706")), listOf("8002776", "8006379", "8002293", "8002323", "8004173", "8004152", "8004153")),
        MultiColorLabelSegment(listOf(ColoredPart("S8", "#D97706")), listOf("8004168", "8004145", "8004146", "8004147", "8005971", "8003084", "8002546", "8004167")),
        MultiColorLabelSegment(listOf(ColoredPart("S20", "#ED6B83")), listOf("8004151", "8004137", "8004156")),
        MultiColorLabelSegment(
            listOf(
                ColoredPart("S1", "#16BAE7"),
                ColoredPart("S2", "#76B82A"),
                ColoredPart("S3", "#951B81"),
                ColoredPart("S4", "#E30613")
            ),
            listOf("8004133", "8004134", "8004128", "8004129", "8000261", "8004139", "8004135", "8004132", "8004136", "8000262")
        )
    )

    // Stations whose marker IS directly the colored terminal badge (zero duplicate circle!)
    private val sBahnTerminalStationIds: Set<String> = setOf(
        "8002080", // Freising (S1)
        "8004166", // Flughafen München (S1, S8)
        "8004781", // Petershausen (S2)
        "8001815", // Erding (S2)
        "8003852", // Mammendorf (S3)
        "8000174", // Holzkirchen (S3)
        "8000121", // Geltendorf (S4)
        "8001638", // Ebersberg (S4, S6)
        "8000155", // Tutzing (S6)
        "8006560", // Wolfratshausen (S7)
        "8002776"  // Herrsching (S8)
    )

    // ---- Munich Regio (RE, RB) & Fernverkehr (ICE, IC/EC) Base Corridors ----

    val regioLongStations: List<TransitStation> = listOf(
        TransitStation("8000261", "München Hbf", GeoPoint(48.1402, 11.5599), StationType.LONG_DISTANCE, listOf("ICE", "IC", "RE 1", "RE 2", "RB 33", "RE 5", "RE 9"), "Ludwigsvorstadt"),
        TransitStation("8004151", "München-Pasing", GeoPoint(48.1499, 11.4616), StationType.LONG_DISTANCE, listOf("ICE", "IC", "RE 9", "RB 6"), "Pasing"),
        TransitStation("8000262", "München Ost", GeoPoint(48.1272, 11.6048), StationType.LONG_DISTANCE, listOf("ICE", "EC", "RE 5"), "Haidhausen"),
        TransitStation("8004126", "Moosach", GeoPoint(48.1802, 11.5067), StationType.REGIO, listOf("RB 33"), "Moosach"),
        TransitStation("8004130", "Feldmoching", GeoPoint(48.2138, 11.5416), StationType.REGIO, listOf("RB 33"), "Feldmoching"),
        TransitStation("8002080", "Freising", GeoPoint(48.3958, 11.7453), StationType.REGIO, listOf("RE 2", "RE 3", "RB 33"), "Freising"),
        TransitStation("8000217", "Landshut (Bay) Hbf", GeoPoint(48.5444, 12.1422), StationType.REGIO, listOf("RE 2", "RE 3", "RB 33"), "Landshut"),
        TransitStation("8001342", "Dachau Bahnhof", GeoPoint(48.2546, 11.4443), StationType.REGIO, listOf("RE 1", "RB 16"), "Dachau"),
        TransitStation("8004781", "Petershausen", GeoPoint(48.4106, 11.4706), StationType.REGIO, listOf("RE 1", "RB 16"), "Petershausen"),
        TransitStation("8000183", "Ingolstadt Hbf", GeoPoint(48.7444, 11.4373), StationType.LONG_DISTANCE, listOf("ICE", "RE 1", "RB 16"), "Ingolstadt"),
        TransitStation("8000013", "Augsburg Hbf", GeoPoint(48.3654, 10.8856), StationType.LONG_DISTANCE, listOf("ICE", "IC", "RE 9"), "Augsburg"),
        TransitStation("8000130", "Grafing Bahnhof", GeoPoint(48.0439, 11.9438), StationType.REGIO, listOf("RE 5", "RB 54"), "Grafing"),
        TransitStation("8000320", "Rosenheim", GeoPoint(47.8501, 12.1192), StationType.LONG_DISTANCE, listOf("ICE", "EC", "RE 5", "RB 54"), "Rosenheim"),
        TransitStation("8000155", "Tutzing", GeoPoint(47.9087, 11.2747), StationType.REGIO, listOf("RB 6"), "Tutzing"),
        TransitStation("8000667", "Weilheim (Oberbay)", GeoPoint(47.8406, 11.1362), StationType.REGIO, listOf("RB 6"), "Weilheim")
    )

    private val regioStationsById: Map<String, TransitStation> = regioLongStations.associateBy { it.id }

    private val regioTerminalStationIds: Set<String> = setOf(
        "8000217", // Landshut (RE 2, RB 33)
        "8000183", // Ingolstadt (ICE, RE 1)
        "8000013", // Augsburg (ICE, RE 9)
        "8000320", // Rosenheim (ICE, RE 5)
        "8000667"  // Weilheim (RB 6)
    )

    private val regioOffsetSegments: List<OffsetSegment> = listOf(
        // Fast Regio Munich Hbf -> Moosach -> Feldmoching -> Freising -> Landshut (RE 2 & RB 33 parallel!)
        OffsetSegment("RE 2", "#C8102E", -0.55, listOf("8000261", "8004126", "8004130", "8002080", "8000217")),
        OffsetSegment("RB 33", "#1D4ED8", 0.55, listOf("8000261", "8004126", "8004130", "8002080", "8000217")),
        // Munich Hbf -> Dachau -> Petershausen -> Ingolstadt (ICE & RE 1 parallel!)
        OffsetSegment("ICE", "#D5001C", -0.6, listOf("8000261", "8001342", "8004781", "8000183")),
        OffsetSegment("RE 1", "#0F766E", 0.6, listOf("8000261", "8001342", "8004781", "8000183")),
        // Munich Hbf -> Pasing -> Augsburg (ICE & RE 9 parallel!)
        OffsetSegment("ICE", "#D5001C", -0.6, listOf("8000261", "8004151", "8000013")),
        OffsetSegment("RE 9", "#2563EB", 0.6, listOf("8000261", "8004151", "8000013")),
        // Munich Hbf -> Ost -> Grafing -> Rosenheim (ICE/EC & RE 5 parallel!)
        OffsetSegment("ICE", "#D5001C", -0.6, listOf("8000261", "8000262", "8000130", "8000320")),
        OffsetSegment("RE 5", "#0284C7", 0.6, listOf("8000261", "8000262", "8000130", "8000320")),
        // Munich Hbf -> Pasing -> Tutzing -> Weilheim (RB 6)
        OffsetSegment("RB 6", "#1D4ED8", 0.0, listOf("8000261", "8004151", "8000155", "8000667"))
    )

    private val regioMultiColorLabels: List<MultiColorLabelSegment> = listOf(
        MultiColorLabelSegment(listOf(ColoredPart("RE 2", "#C8102E"), ColoredPart("RB 33", "#1D4ED8")), listOf("8000261", "8004126", "8004130", "8002080", "8000217")),
        MultiColorLabelSegment(listOf(ColoredPart("ICE", "#D5001C"), ColoredPart("RE 1", "#0F766E")), listOf("8000261", "8001342", "8004781", "8000183")),
        MultiColorLabelSegment(listOf(ColoredPart("ICE", "#D5001C"), ColoredPart("RE 9", "#2563EB")), listOf("8000261", "8004151", "8000013")),
        MultiColorLabelSegment(listOf(ColoredPart("ICE", "#D5001C"), ColoredPart("RE 5", "#0284C7")), listOf("8000261", "8000262", "8000130", "8000320")),
        MultiColorLabelSegment(listOf(ColoredPart("RB 6", "#1D4ED8")), listOf("8004151", "8000155", "8000667"))
    )

    // ---- Key Munich MetroBus / StadtBus Corridors (with parallel offset & multi-color labels) ----

    data class BusCorridorStop(
        val id: String,
        val name: String,
        val location: GeoPoint,
        val lines: List<String>
    )

    val busCorridorStops: List<BusCorridorStop> = listOf(
        BusCorridorStop("bus_dessauer", "Dessauerstraße", GeoPoint(48.1830, 11.5198), listOf("50", "60")),
        BusCorridorStop("bus_oez", "Olympia-Einkaufszentrum", GeoPoint(48.1807, 11.5307), listOf("50", "60", "175")),
        BusCorridorStop("bus_oberwiesenfeld", "Oberwiesenfeld", GeoPoint(48.1864, 11.5476), listOf("50")),
        BusCorridorStop("bus_frankfurter_ring", "Frankfurter Ring", GeoPoint(48.1873, 11.5726), listOf("50", "177")),
        BusCorridorStop("bus_euro_industriepark", "Euro-Industriepark", GeoPoint(48.1912, 11.5920), listOf("50", "177")),
        BusCorridorStop("bus_domagkstr", "Domagkstraße", GeoPoint(48.1868, 11.6012), listOf("50", "177")),
        BusCorridorStop("bus_studentenstadt", "Studentenstadt", GeoPoint(48.1835, 11.6080), listOf("50", "177", "181")),
        BusCorridorStop("bus_st_emmeram", "St. Emmeram", GeoPoint(48.1838, 11.6308), listOf("50")),
        BusCorridorStop("bus_johanneskirchen", "Johanneskirchen Bf.", GeoPoint(48.1704, 11.6458), listOf("50", "154")),
        BusCorridorStop("bus_petuelring", "Petuelring", GeoPoint(48.1757, 11.5663), listOf("177", "178")),
        BusCorridorStop("bus_milbertshofen", "Milbertshofen", GeoPoint(48.1806, 11.5732), listOf("177")),
        BusCorridorStop("bus_feldmoching", "Feldmoching Bf.", GeoPoint(48.2138, 11.5416), listOf("172", "173")),
        BusCorridorStop("bus_hasenbergl", "Hasenbergl", GeoPoint(48.2121, 11.5567), listOf("60")),
        BusCorridorStop("bus_duelferstr", "Dülferstraße", GeoPoint(48.2128, 11.5628), listOf("60", "173")),
        BusCorridorStop("bus_mira", "Mira Einkaufszentrum", GeoPoint(48.2116, 11.5680), listOf("60", "173")),
        BusCorridorStop("bus_hart", "Am Hart", GeoPoint(48.1972, 11.5720), listOf("171", "172")),
        BusCorridorStop("bus_olympiazentrum", "Olympiazentrum", GeoPoint(48.1801, 11.5554), listOf("173")),
        BusCorridorStop("bus_muenchner_freiheit", "Münchner Freiheit", GeoPoint(48.1619, 11.5861), listOf("53", "54", "59")),
        BusCorridorStop("bus_chinesischer_turm", "Chinesischer Turm", GeoPoint(48.1524, 11.5920), listOf("54", "58", "68")),
        BusCorridorStop("bus_herkomerplatz", "Herkomerplatz", GeoPoint(48.1536, 11.6085), listOf("54", "154", "187")),
        BusCorridorStop("bus_prinze", "Prinzregentenplatz", GeoPoint(48.1391, 11.6069), listOf("54", "100")),
        BusCorridorStop("bus_ostbahnhof", "Ostbahnhof", GeoPoint(48.1277, 11.6042), listOf("54", "55", "190")),
        BusCorridorStop("bus_giesing", "Giesing Bf.", GeoPoint(48.1109, 11.5956), listOf("54", "59", "139")),
        BusCorridorStop("bus_harras", "Harras", GeoPoint(48.1173, 11.5369), listOf("53", "54", "130")),
        BusCorridorStop("bus_lorettoplatz", "Lorettoplatz", GeoPoint(48.1157, 11.5008), listOf("54")),
        BusCorridorStop("bus_berg_am_laim", "Berg am Laim Bf.", GeoPoint(48.1337, 11.6335), listOf("185", "187", "190", "191")),
        BusCorridorStop("bus_zamdorf", "Zamdorf Siedlung", GeoPoint(48.1376, 11.6486), listOf("190", "191")),
        BusCorridorStop("bus_riem_bf", "Riem Bf.", GeoPoint(48.1445, 11.6787), listOf("190")),
        BusCorridorStop("bus_messestadt_west", "Messestadt West", GeoPoint(48.1332, 11.6912), listOf("190", "191")),
        BusCorridorStop("bus_messestadt_ost", "Messestadt Ost", GeoPoint(48.1330, 11.7037), listOf("190"))
    )

    private val busStopsById: Map<String, BusCorridorStop> = busCorridorStops.associateBy { it.id }

    private val busTerminalStopIds: Set<String> = setOf(
        "bus_dessauer",
        "bus_johanneskirchen",
        "bus_petuelring",
        "bus_studentenstadt",
        "bus_feldmoching",
        "bus_olympiazentrum",
        "bus_mira",
        "bus_muenchner_freiheit",
        "bus_lorettoplatz",
        "bus_messestadt_ost",
        "bus_messestadt_west"
    )

    // Parallel offset bus lines with distinct official MetroBus/StadtBus colors
    private val busOffsetSegments: List<OffsetSegment> = listOf(
        // Shared Bus 50 + 60
        OffsetSegment("50", "#00586A", -0.5, listOf("bus_dessauer", "bus_oez")),
        OffsetSegment("60", "#0369A1", 0.5, listOf("bus_dessauer", "bus_oez")),
        // Bus 50 West
        OffsetSegment("50", "#00586A", 0.0, listOf("bus_oez", "bus_oberwiesenfeld", "bus_frankfurter_ring")),
        // Shared Bus 50 + 177 (parallel!)
        OffsetSegment("50", "#00586A", -0.5, listOf("bus_frankfurter_ring", "bus_euro_industriepark", "bus_domagkstr", "bus_studentenstadt")),
        OffsetSegment("177", "#6D28D9", 0.5, listOf("bus_frankfurter_ring", "bus_euro_industriepark", "bus_domagkstr", "bus_studentenstadt")),
        // Bus 50 East
        OffsetSegment("50", "#00586A", 0.0, listOf("bus_studentenstadt", "bus_st_emmeram", "bus_johanneskirchen")),
        // Bus 177 South
        OffsetSegment("177", "#6D28D9", 0.0, listOf("bus_petuelring", "bus_milbertshofen", "bus_frankfurter_ring")),
        // Bus 60 & 173
        OffsetSegment("60", "#0369A1", 0.0, listOf("bus_oez", "bus_hasenbergl", "bus_duelferstr")),
        OffsetSegment("60", "#0369A1", -0.5, listOf("bus_duelferstr", "bus_mira")),
        OffsetSegment("173", "#0D9488", 0.5, listOf("bus_duelferstr", "bus_mira")),
        OffsetSegment("173", "#0D9488", 0.0, listOf("bus_feldmoching", "bus_duelferstr")),
        OffsetSegment("173", "#0D9488", 0.0, listOf("bus_mira", "bus_hart", "bus_olympiazentrum")),
        // Bus 54 Ring
        OffsetSegment("54", "#00586A", 0.0, listOf("bus_muenchner_freiheit", "bus_chinesischer_turm", "bus_herkomerplatz", "bus_prinze", "bus_ostbahnhof", "bus_giesing", "bus_harras", "bus_lorettoplatz")),
        // Shared Bus 190 + 191 (parallel!)
        OffsetSegment("190", "#00586A", -0.5, listOf("bus_ostbahnhof", "bus_berg_am_laim", "bus_zamdorf")),
        OffsetSegment("191", "#0891B2", 0.5, listOf("bus_ostbahnhof", "bus_berg_am_laim", "bus_zamdorf")),
        OffsetSegment("190", "#00586A", 0.0, listOf("bus_zamdorf", "bus_riem_bf", "bus_messestadt_west", "bus_messestadt_ost")),
        OffsetSegment("191", "#0891B2", 0.0, listOf("bus_zamdorf", "bus_messestadt_west"))
    )

    private val busMultiColorLabels: List<MultiColorLabelSegment> = listOf(
        MultiColorLabelSegment(listOf(ColoredPart("50", "#00586A"), ColoredPart("60", "#0369A1")), listOf("bus_dessauer", "bus_oez")),
        MultiColorLabelSegment(listOf(ColoredPart("Bus 50", "#00586A")), listOf("bus_oez", "bus_oberwiesenfeld", "bus_frankfurter_ring")),
        MultiColorLabelSegment(listOf(ColoredPart("50", "#00586A"), ColoredPart("177", "#6D28D9")), listOf("bus_frankfurter_ring", "bus_euro_industriepark", "bus_domagkstr", "bus_studentenstadt")),
        MultiColorLabelSegment(listOf(ColoredPart("Bus 50", "#00586A")), listOf("bus_studentenstadt", "bus_st_emmeram", "bus_johanneskirchen")),
        MultiColorLabelSegment(listOf(ColoredPart("Bus 177", "#6D28D9")), listOf("bus_petuelring", "bus_milbertshofen", "bus_frankfurter_ring")),
        MultiColorLabelSegment(listOf(ColoredPart("Bus 60", "#0369A1")), listOf("bus_oez", "bus_hasenbergl", "bus_duelferstr")),
        MultiColorLabelSegment(listOf(ColoredPart("60", "#0369A1"), ColoredPart("173", "#0D9488")), listOf("bus_duelferstr", "bus_mira")),
        MultiColorLabelSegment(listOf(ColoredPart("Bus 173", "#0D9488")), listOf("bus_feldmoching", "bus_duelferstr")),
        MultiColorLabelSegment(listOf(ColoredPart("Bus 173", "#0D9488")), listOf("bus_mira", "bus_hart", "bus_olympiazentrum")),
        MultiColorLabelSegment(listOf(ColoredPart("Bus 54", "#00586A")), listOf("bus_muenchner_freiheit", "bus_chinesischer_turm", "bus_herkomerplatz", "bus_prinze", "bus_ostbahnhof", "bus_giesing", "bus_harras", "bus_lorettoplatz")),
        MultiColorLabelSegment(listOf(ColoredPart("190", "#00586A"), ColoredPart("191", "#0891B2")), listOf("bus_ostbahnhof", "bus_berg_am_laim", "bus_zamdorf")),
        MultiColorLabelSegment(listOf(ColoredPart("Bus 190", "#00586A")), listOf("bus_zamdorf", "bus_riem_bf", "bus_messestadt_west", "bus_messestadt_ost")),
        MultiColorLabelSegment(listOf(ColoredPart("Bus 191", "#0891B2")), listOf("bus_zamdorf", "bus_messestadt_west"))
    )

    val busTerminalStopsAsStations: List<TransitStation> = busCorridorStops.map {
        TransitStation(
            id = it.id,
            name = it.name,
            location = it.location,
            type = StationType.BUS,
            lines = it.lines.map { ln -> "Bus $ln" }
        )
    }

    val districts: List<MunichDistrict> = listOf(
        MunichDistrict("Feldmoching", GeoPoint(48.2135, 11.5310)),
        MunichDistrict("Hasenbergl", GeoPoint(48.2122, 11.5595)),
        MunichDistrict("Riem", GeoPoint(48.1375, 11.6920)),
        MunichDistrict("Messestadt", GeoPoint(48.1325, 11.7010)),
        MunichDistrict("Schwabing", GeoPoint(48.1650, 11.5860)),
        MunichDistrict("Maxvorstadt", GeoPoint(48.1495, 11.5680)),
        MunichDistrict("Sendling", GeoPoint(48.1170, 11.5415)),
        MunichDistrict("Giesing", GeoPoint(48.1120, 11.5840)),
        MunichDistrict("Haidhausen", GeoPoint(48.1305, 11.5980)),
        MunichDistrict("Bogenhausen", GeoPoint(48.1550, 11.6220)),
        MunichDistrict("Trudering", GeoPoint(48.1205, 11.6640)),
        MunichDistrict("Neuperlach", GeoPoint(48.0980, 11.6470)),
        MunichDistrict("Moosach", GeoPoint(48.1815, 11.5150)),
        MunichDistrict("Milbertshofen", GeoPoint(48.1865, 11.5675)),
        MunichDistrict("Freimann", GeoPoint(48.1945, 11.6175)),
        MunichDistrict("Neuhausen", GeoPoint(48.1535, 11.5320)),
        MunichDistrict("Nymphenburg", GeoPoint(48.1580, 11.5015)),
        MunichDistrict("Laim", GeoPoint(48.1400, 11.4980)),
        MunichDistrict("Pasing", GeoPoint(48.1465, 11.4585)),
        MunichDistrict("Aubing", GeoPoint(48.1520, 11.4150)),
        MunichDistrict("Allach", GeoPoint(48.1940, 11.4640)),
        MunichDistrict("Hadern", GeoPoint(48.1180, 11.4820)),
        MunichDistrict("Solln", GeoPoint(48.0790, 11.5180)),
        MunichDistrict("Garching", GeoPoint(48.2490, 11.6510)),
        MunichDistrict("Unterföhring", GeoPoint(48.1905, 11.6500)),
        MunichDistrict("Haar", GeoPoint(48.1105, 11.7320)),
        MunichDistrict("Unterhaching", GeoPoint(48.0660, 11.6140)),
        MunichDistrict("Dachau", GeoPoint(48.2600, 11.4340))
    )

    // ---- GeoJSON Builders ----

    fun buildLinesGeoJson(): String = buildOffsetLinesGeoJson(sBahnOffsetSegments) { stationsById[it]?.location }

    fun buildSBahnLineLabelsGeoJson(): String = buildMultiColorLabelsGeoJson(sBahnMultiColorLabels) { stationsById[it]?.location }

    fun buildSBahnTerminalBadgesGeoJson(): String {
        val badges = listOfNotNull(
            // Single-line terminals sit directly AT the station dot (offsetLon = 0.0 -> zero duplicate circle!)
            sBahnBadge("8002080", "S1", "#16BAE7", "#FFFFFF"),
            sBahnBadge("8004781", "S2", "#76B82A", "#FFFFFF"),
            sBahnBadge("8001815", "S2", "#76B82A", "#FFFFFF"),
            sBahnBadge("8003852", "S3", "#951B81", "#FFFFFF"),
            sBahnBadge("8000174", "S3", "#951B81", "#FFFFFF"),
            sBahnBadge("8000121", "S4", "#E30613", "#FFFFFF"),
            sBahnBadge("8000155", "S6", "#00975F", "#FFFFFF"),
            sBahnBadge("8006560", "S7", "#963833", "#FFFFFF"),
            sBahnBadge("8002776", "S8", "#F7A600", "#111315"),
            // Shared 2-line terminals sit tightly side-by-side replacing the dot
            sBahnBadge("8004166", "S1", "#16BAE7", "#FFFFFF", offsetLon = -0.0045),
            sBahnBadge("8004166", "S8", "#F7A600", "#111315", offsetLon = 0.0045),
            sBahnBadge("8001638", "S4", "#E30613", "#FFFFFF", offsetLon = -0.0045),
            sBahnBadge("8001638", "S6", "#00975F", "#FFFFFF", offsetLon = 0.0045)
        )
        return buildBadgesGeoJson(badges)
    }

    fun buildStationsGeoJson(): String {
        val features = JSONArray()
        stations.forEach { st ->
            val primaryColor = sBahnOffsetSegments.firstOrNull { it.line == st.lines.firstOrNull() }?.colorHex ?: "#0065BD"
            val isTerminal = sBahnTerminalStationIds.contains(st.id)
            features.put(
                JSONObject().apply {
                    put("type", "Feature")
                    put("properties", JSONObject().apply {
                        put("id", st.id)
                        put("name", st.name)
                        put("lines", st.lines.joinToString(" · "))
                        put("color", primaryColor)
                        put("isTerminal", isTerminal)
                    })
                    put("geometry", JSONObject().apply {
                        put("type", "Point")
                        put("coordinates", JSONArray().put(st.location.longitude).put(st.location.latitude))
                    })
                }
            )
        }
        return wrapFeatureCollection(features)
    }

    fun buildRegioLinesGeoJson(): String = buildOffsetLinesGeoJson(regioOffsetSegments) { regioStationsById[it]?.location }

    fun buildRegioLineLabelsGeoJson(): String = buildMultiColorLabelsGeoJson(regioMultiColorLabels) { regioStationsById[it]?.location }

    fun buildRegioStationsGeoJson(): String {
        val features = JSONArray()
        regioLongStations.forEach { st ->
            val isLongDist = st.type == StationType.LONG_DISTANCE
            val color = if (isLongDist) "#D5001C" else "#1D4ED8"
            features.put(
                JSONObject().apply {
                    put("type", "Feature")
                    put("properties", JSONObject().apply {
                        put("id", st.id)
                        put("name", st.name)
                        put("color", color)
                        put("isTerminal", regioTerminalStationIds.contains(st.id))
                    })
                    put("geometry", JSONObject().apply {
                        put("type", "Point")
                        put("coordinates", JSONArray().put(st.location.longitude).put(st.location.latitude))
                    })
                }
            )
        }
        return wrapFeatureCollection(features)
    }

    fun buildRegioTerminalBadgesGeoJson(): String {
        val badges = listOfNotNull(
            regioBadge("8000217", "RE2", "#C8102E", offsetLon = -0.008),
            regioBadge("8000217", "RB33", "#1D4ED8", offsetLon = 0.008),
            regioBadge("8000183", "ICE", "#D5001C", offsetLon = -0.008),
            regioBadge("8000183", "RE1", "#0F766E", offsetLon = 0.008),
            regioBadge("8000013", "ICE", "#D5001C", offsetLon = -0.008),
            regioBadge("8000013", "RE9", "#2563EB", offsetLon = 0.008),
            regioBadge("8000320", "ICE", "#D5001C", offsetLon = -0.008),
            regioBadge("8000320", "RE5", "#0284C7", offsetLon = 0.008),
            regioBadge("8000667", "RB6", "#1D4ED8", offsetLon = 0.0)
        )
        return buildBadgesGeoJson(badges)
    }

    fun buildBusLinesGeoJson(): String = buildOffsetLinesGeoJson(busOffsetSegments) { busStopsById[it]?.location }

    fun buildBusLineLabelsGeoJson(): String = buildMultiColorLabelsGeoJson(busMultiColorLabels) { busStopsById[it]?.location }

    fun buildBusTerminalBadgesGeoJson(): String {
        val badges = listOfNotNull(
            busBadge("bus_dessauer", "50", "#00586A", offsetLon = -0.0022),
            busBadge("bus_dessauer", "60", "#0369A1", offsetLon = 0.0022),
            busBadge("bus_johanneskirchen", "50", "#00586A"),
            busBadge("bus_petuelring", "177", "#6D28D9"),
            busBadge("bus_studentenstadt", "177", "#6D28D9"),
            busBadge("bus_feldmoching", "173", "#0D9488"),
            busBadge("bus_olympiazentrum", "173", "#0D9488"),
            busBadge("bus_mira", "60", "#0369A1"),
            busBadge("bus_muenchner_freiheit", "54", "#00586A"),
            busBadge("bus_lorettoplatz", "54", "#00586A"),
            busBadge("bus_messestadt_ost", "190", "#00586A"),
            busBadge("bus_messestadt_west", "191", "#0891B2")
        )
        return buildBadgesGeoJson(badges)
    }

    fun isBusTerminalStop(stopId: String): Boolean = busTerminalStopIds.contains(stopId)

    fun buildDistrictsGeoJson(): String {
        val features = JSONArray()
        districts.forEach { d ->
            features.put(
                JSONObject().apply {
                    put("type", "Feature")
                    put("properties", JSONObject().apply { put("name", d.name.uppercase()) })
                    put("geometry", JSONObject().apply {
                        put("type", "Point")
                        put("coordinates", JSONArray().put(d.location.longitude).put(d.location.latitude))
                    })
                }
            )
        }
        return wrapFeatureCollection(features)
    }

    private fun sBahnBadge(
        stationId: String,
        label: String,
        colorHex: String,
        textColorHex: String,
        offsetLon: Double = 0.0
    ): IntegratedTerminalBadge? {
        val st = stationsById[stationId] ?: return null
        return IntegratedTerminalBadge(stationId, st.name, label, colorHex, textColorHex, st.location, offsetLon)
    }

    private fun regioBadge(
        stationId: String,
        label: String,
        colorHex: String,
        offsetLon: Double = 0.0
    ): IntegratedTerminalBadge? {
        val st = regioStationsById[stationId] ?: return null
        return IntegratedTerminalBadge(stationId, st.name, label, colorHex, "#FFFFFF", st.location, offsetLon)
    }

    private fun busBadge(
        stopId: String,
        label: String,
        colorHex: String,
        offsetLon: Double = 0.0
    ): IntegratedTerminalBadge? {
        val st = busStopsById[stopId] ?: return null
        return IntegratedTerminalBadge(stopId, st.name, label, colorHex, "#FFFFFF", st.location, offsetLon)
    }

    private fun buildOffsetLinesGeoJson(
        segments: List<OffsetSegment>,
        resolver: (String) -> GeoPoint?
    ): String {
        val features = JSONArray()
        segments.forEach { seg ->
            val coords = JSONArray()
            seg.stationIds.forEach { id ->
                resolver(id)?.let { loc ->
                    coords.put(JSONArray().put(loc.longitude).put(loc.latitude))
                }
            }
            if (coords.length() >= 2) {
                features.put(
                    JSONObject().apply {
                        put("type", "Feature")
                        put("properties", JSONObject().apply {
                            put("line", seg.line)
                            put("color", seg.colorHex)
                            put("offsetIndex", seg.offsetIndex)
                        })
                        put("geometry", JSONObject().apply {
                            put("type", "LineString")
                            put("coordinates", coords)
                        })
                    }
                )
            }
        }
        return wrapFeatureCollection(features)
    }

    private fun buildMultiColorLabelsGeoJson(
        segments: List<MultiColorLabelSegment>,
        resolver: (String) -> GeoPoint?
    ): String {
        val features = JSONArray()
        segments.forEach { seg ->
            val coords = JSONArray()
            seg.stationIds.forEach { id ->
                resolver(id)?.let { loc ->
                    coords.put(JSONArray().put(loc.longitude).put(loc.latitude))
                }
            }
            if (coords.length() >= 2) {
                val props = JSONObject()
                for (slot in 1..4) {
                    val part = seg.parts.getOrNull(slot - 1)
                    props.put("p$slot", part?.label ?: "")
                    props.put("c$slot", part?.colorHex ?: "#0065BD")
                }
                features.put(
                    JSONObject().apply {
                        put("type", "Feature")
                        put("properties", props)
                        put("geometry", JSONObject().apply {
                            put("type", "LineString")
                            put("coordinates", coords)
                        })
                    }
                )
            }
        }
        return wrapFeatureCollection(features)
    }

    private fun buildBadgesGeoJson(badges: List<IntegratedTerminalBadge>): String {
        val features = JSONArray()
        badges.forEach { b ->
            features.put(
                JSONObject().apply {
                    put("type", "Feature")
                    put("properties", JSONObject().apply {
                        put("label", b.label)
                        put("stationName", b.stationName)
                        put("color", b.colorHex)
                        put("textColor", b.textColorHex)
                    })
                    put("geometry", JSONObject().apply {
                        put("type", "Point")
                        put(
                            "coordinates",
                            JSONArray()
                                .put(b.location.longitude + b.offsetLon)
                                .put(b.location.latitude)
                        )
                    })
                }
            )
        }
        return wrapFeatureCollection(features)
    }

    private fun wrapFeatureCollection(features: JSONArray): String {
        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()
    }
}
