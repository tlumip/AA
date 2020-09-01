package simpleorm.quickstart;

public class HBAFormatter implements INiceNameFormatter {

	@Override
	public String niceNameForTable(String table) {
		String[] parts = table.split("_");
		StringBuffer newName = new StringBuffer();
		for (String part : parts) {
			for (int i = 0; i< part.length(); i++) {
				if (i==0) newName.append(Character.toUpperCase(part.charAt(i)));
				else newName.append(part.charAt(i));
			}
		}
		return newName.toString();
	}

	@Override
	public String niceNameForColumn(String table, String column) {
		String[] parts = column.split("_");
		StringBuffer newName = new StringBuffer();
		boolean firstPart = true;
		for (String part : parts) {
			for (int i = 0; i< part.length(); i++) {
				if (i==0 && !firstPart) newName.append(Character.toUpperCase(part.charAt(i)));
				else newName.append(part.charAt(i));
			}
			firstPart = false;
		}
		return newName.toString();
	}

	@Override
	public String niceNameForForeignKey(String localTable, String foreignTable) {
		String[] parts = (localTable + "_FK_" + foreignTable).split("_");
		StringBuffer newName = new StringBuffer();
		boolean firstPart = true;
		for (String part : parts) {
			for (int i = 0; i< part.length(); i++) {
				if (i==0 && !firstPart) newName.append(Character.toUpperCase(part.charAt(i)));
				else newName.append(part.charAt(i));
			}
			firstPart = false;
		}
		return newName.toString();
	}

}
