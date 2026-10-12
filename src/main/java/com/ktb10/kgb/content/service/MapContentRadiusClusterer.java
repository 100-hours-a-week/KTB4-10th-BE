package com.ktb10.kgb.content.service;

import com.ktb10.kgb.content.dto.MapClusterResponse;
import com.ktb10.kgb.content.repository.MapContentCommonData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 화면 픽셀 거리를 기준으로 지도 콘텐츠를 원형에 가까운 형태로 묶습니다. */
@Component
public class MapContentRadiusClusterer {

    private static final double WORLD_TILE_SIZE_PIXELS = 256.0;
    private static final double RADIUS_PIXELS = 120.0;
    private static final double MAX_MERCATOR_LATITUDE = 85.05112878;
    private static final int KAKAO_TO_MERCATOR_ZOOM_OFFSET = 2;

    public List<Result> cluster(List<MapContentCommonData> contents, int zoom) {
        List<ProjectedContent> projectedContents = contents.stream()
                .sorted(Comparator.comparing(MapContentCommonData::id))
                .map(content -> project(content, zoom))
                .toList();
        Map<Cell, List<MutableCluster>> buckets = new HashMap<>();
        List<MutableCluster> clusters = new ArrayList<>();

        for (ProjectedContent projected : projectedContents) {
            Cell cell = cellOf(projected.x(), projected.y());
            MutableCluster nearest = nearestCluster(projected, cell, buckets);
            if (nearest == null) {
                MutableCluster cluster = new MutableCluster(projected);
                clusters.add(cluster);
                buckets.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(cluster);
                continue;
            }

            Cell previousCell = cellOf(nearest.centerX(), nearest.centerY());
            nearest.add(projected);
            Cell currentCell = cellOf(nearest.centerX(), nearest.centerY());
            if (!previousCell.equals(currentCell)) {
                buckets.get(previousCell).remove(nearest);
                buckets.computeIfAbsent(currentCell, ignored -> new ArrayList<>()).add(nearest);
            }
        }

        return clusters.stream()
                .map(cluster -> cluster.toResult(zoom))
                .sorted(Comparator.comparingInt((Result result) -> result.cluster().count())
                        .reversed()
                        .thenComparing(result -> result.cluster().clusterId()))
                .toList();
    }

    private MutableCluster nearestCluster(
            ProjectedContent content,
            Cell cell,
            Map<Cell, List<MutableCluster>> buckets) {
        MutableCluster nearest = null;
        double nearestDistanceSquared = RADIUS_PIXELS * RADIUS_PIXELS;
        for (long x = cell.x() - 1; x <= cell.x() + 1; x++) {
            for (long y = cell.y() - 1; y <= cell.y() + 1; y++) {
                for (MutableCluster candidate : buckets.getOrDefault(new Cell(x, y), List.of())) {
                    double distanceSquared = squaredDistance(
                            content.x(), content.y(), candidate.centerX(), candidate.centerY());
                    if (distanceSquared <= nearestDistanceSquared) {
                        nearest = candidate;
                        nearestDistanceSquared = distanceSquared;
                    }
                }
            }
        }
        return nearest;
    }

    private ProjectedContent project(MapContentCommonData content, int zoom) {
        double worldSize = worldSize(zoom);
        double latitude = Math.max(
                -MAX_MERCATOR_LATITUDE,
                Math.min(MAX_MERCATOR_LATITUDE, content.latitude()));
        double sinLatitude = Math.sin(Math.toRadians(latitude));
        double x = (content.longitude() + 180.0) / 360.0 * worldSize;
        double y = (0.5 - Math.log((1.0 + sinLatitude) / (1.0 - sinLatitude))
                / (4.0 * Math.PI)) * worldSize;
        return new ProjectedContent(content, x, y);
    }

    private static double worldSize(int zoom) {
        return WORLD_TILE_SIZE_PIXELS * Math.pow(2.0, zoom - KAKAO_TO_MERCATOR_ZOOM_OFFSET);
    }

    private Cell cellOf(double x, double y) {
        return new Cell((long) Math.floor(x / RADIUS_PIXELS),
                (long) Math.floor(y / RADIUS_PIXELS));
    }

    private static double squaredDistance(double x1, double y1, double x2, double y2) {
        double deltaX = x1 - x2;
        double deltaY = y1 - y2;
        return deltaX * deltaX + deltaY * deltaY;
    }

    public record Result(MapClusterResponse cluster, MapContentCommonData representative) {
    }

    private record Cell(long x, long y) {
    }

    private record ProjectedContent(MapContentCommonData content, double x, double y) {
    }

    private static final class MutableCluster {
        private final List<ProjectedContent> contents = new ArrayList<>();
        private double sumX;
        private double sumY;

        private MutableCluster(ProjectedContent content) {
            add(content);
        }

        private void add(ProjectedContent content) {
            contents.add(content);
            sumX += content.x();
            sumY += content.y();
        }

        private double centerX() {
            return sumX / contents.size();
        }

        private double centerY() {
            return sumY / contents.size();
        }

        private Result toResult(int zoom) {
            double worldSize = worldSize(zoom);
            double longitude = centerX() / worldSize * 360.0 - 180.0;
            double mercator = Math.PI - 2.0 * Math.PI * centerY() / worldSize;
            double latitude = Math.toDegrees(Math.atan(Math.sinh(mercator)));
            ProjectedContent representative = contents.stream()
                    .min(Comparator.<ProjectedContent>comparingDouble(content -> squaredDistance(
                                    content.x(), content.y(), centerX(), centerY()))
                            .thenComparing(content -> content.content().id()))
                    .orElseThrow();
            long minimumId = contents.stream()
                    .map(ProjectedContent::content)
                    .mapToLong(MapContentCommonData::id)
                    .min()
                    .orElseThrow();
            return new Result(
                    new MapClusterResponse(
                            "z" + zoom + ":" + minimumId,
                            latitude,
                            longitude,
                            contents.size()),
                    representative.content());
        }
    }
}
