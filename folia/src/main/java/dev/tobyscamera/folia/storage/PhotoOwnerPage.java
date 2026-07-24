package dev.tobyscamera.folia.storage;

import java.util.List;

public record PhotoOwnerPage(List<PhotoOwner> owners, boolean hasNext) {
    public PhotoOwnerPage { owners = List.copyOf(owners); }
}
