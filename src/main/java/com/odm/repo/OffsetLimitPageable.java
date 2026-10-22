package com.odm.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

// Pageable driven by a row offset instead of a page number, so offset need not be a multiple of limit
public record OffsetLimitPageable(long offset, int limit, Sort sort) implements Pageable {

	public OffsetLimitPageable {
		if (offset < 0) {
			throw new IllegalArgumentException("offset must not be negative");
		}
		if (limit < 1) {
			throw new IllegalArgumentException("limit must be at least 1");
		}
	}

	@Override
	public int getPageNumber() {
		return (int) (offset / limit);
	}

	@Override
	public int getPageSize() {
		return limit;
	}

	@Override
	public long getOffset() {
		return offset;
	}

	@Override
	public Sort getSort() {
		return sort;
	}

	@Override
	public Pageable next() {
		return new OffsetLimitPageable(offset + limit, limit, sort);
	}

	@Override
	public Pageable previousOrFirst() {
		return hasPrevious() ? new OffsetLimitPageable(Math.max(0, offset - limit), limit, sort) : first();
	}

	@Override
	public Pageable first() {
		return new OffsetLimitPageable(0, limit, sort);
	}

	@Override
	public Pageable withPage(int pageNumber) {
		return new OffsetLimitPageable((long) pageNumber * limit, limit, sort);
	}

	@Override
	public boolean hasPrevious() {
		return offset > 0;
	}

}
