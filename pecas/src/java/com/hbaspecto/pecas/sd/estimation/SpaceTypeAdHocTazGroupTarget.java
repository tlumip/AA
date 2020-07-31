package com.hbaspecto.pecas.sd.estimation;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class SpaceTypeAdHocTazGroupTarget extends StandardConstructionTarget {
	private int groupNumber;
	private Set<Integer> group;
	public static final String NAME = "grouptarg";

	public SpaceTypeAdHocTazGroupTarget(int groupNumber, Collection<Integer> group,
			int... spaceTypes) {
		super(SpaceTypeFilter.of(spaceTypes), GeographicFilter.inAdHocTazGroup(groupNumber, group));
	    this.groupNumber = groupNumber;
		this.group = new LinkedHashSet<>(group);
	}

	public Set<Integer> getGroup() {
		return group;
	}

	public int[] getSpaceTypes() {
		return spaceTypeFilter().acceptedSpaceTypeNumbers();
	}

	@Override
	public String getName() {
		return joinHyphens(NAME, groupNumber, getSpaceTypes());
	}
}
