/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */

package com.example.baidu_map.clusterutil.clustering;

import com.baidu.mapapi.model.LatLng;

import java.util.Collection;

/**
 * A collection of ClusterItems that are nearby each other.
 */
public interface Cluster<T extends ClusterItem> {
    public LatLng getPosition();

    Collection<T> getItems();

    int getSize();
}