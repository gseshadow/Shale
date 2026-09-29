package com.shale.core.dto;

import java.util.List;

/** Bounded offset page; total is deliberately omitted to avoid an unbounded count query. */
public record ApplicationInstanceAdminPage(List<AdminApplicationInstanceView> items, int page, int size) {
	public ApplicationInstanceAdminPage { items = List.copyOf(items); }
}
