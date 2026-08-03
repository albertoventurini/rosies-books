package com.albertoventurini.rosiesbooks.library.persistence;

import java.util.UUID;

record CoverAsset(UUID id, byte[] content, String mimeType) {}
