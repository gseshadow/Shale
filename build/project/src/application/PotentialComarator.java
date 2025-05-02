package application;

import java.util.Comparator;

public class PotentialComarator implements Comparator<Potential> {

	@Override
	public int compare(Potential o1, Potential o2) {
		if (o1.getClientNameLast().compareTo(o2.getClientNameLast()) > 0) {
			return 1;
		}
		return 0;
	}
}
