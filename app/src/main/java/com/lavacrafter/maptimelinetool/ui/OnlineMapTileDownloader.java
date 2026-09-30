/*
Copyright 2026 Muchen Jiang (lava-crafter)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.lavacrafter.maptimelinetool.ui;

import android.graphics.drawable.Drawable;
import org.osmdroid.tileprovider.MapTileRequestState;
import org.osmdroid.tileprovider.modules.IFilesystemCache;
import org.osmdroid.tileprovider.modules.INetworkAvailablityCheck;
import org.osmdroid.tileprovider.modules.MapTileDownloader;
import org.osmdroid.tileprovider.tilesource.ITileSource;

// Java keeps osmdroid's public factory/protected TileLoader return type interoperable
// without suppressing Kotlin's exposed-type errors.
public final class OnlineMapTileDownloader extends MapTileDownloader {
    private final TileLoader displayLoader = new TileLoader() {
        @Override
        protected void tileLoaded(MapTileRequestState state, Drawable drawable) {
            completeDownloadedTile(state, drawable);
        }
    };

    public OnlineMapTileDownloader(ITileSource source, IFilesystemCache cache, INetworkAvailablityCheck networkCheck) {
        super(source, cache, networkCheck);
    }

    @Override
    public TileLoader getTileLoader() {
        return displayLoader;
    }

    public void completeDownloadedTile(MapTileRequestState state, Drawable drawable) {
        removeTileFromQueues(state.getMapTile());
        // The default downloader discards this drawable and asks for a disk-cache read.
        // Deliver it directly so it also displays when persistent writes are disabled.
        // The provider's memory cache now owns it; do not recycle it here.
        state.getCallback().mapTileRequestCompleted(state, drawable);
    }
}
