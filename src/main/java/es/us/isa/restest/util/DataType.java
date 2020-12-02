package es.us.isa.restest.util;

/** Data types in Open API specification documents
 * 
 * @author Sergio Segura (original version by Gabriel Ruiz)
 *
 */
public enum DataType {
	
	STRING("string"), INTEGER("integer"), FLOAT("float"), BINARY("binary"),
	LONG("long"), BYTE("byte"), DATE("date"), DOUBLE("double"), INT32("int32"),
	INT64("int64"), NUMBER("number"), UUID("uuid");
	
	private String type;
	
	DataType(String type) {
		this.type = type;
	}
	
	public String getType() {
		return this.type;
	}
	/**
	 * Return the GenerationType using the Open API string type.
	 * @param type The OpenAPI string type
	 * @return GenerationType
	 */
	public static DataType getDataType(String type) {
		DataType result = null;
		for (DataType gt : DataType.values()) {
			if (type.equals(gt.getType())) {
				result = gt;
			}
		}
		return result;
	}
	
	public boolean isNumber() {
		boolean isNumber=false;
		if (this.equals(DataType.INTEGER) || this.equals(DataType.FLOAT) || this.equals(DataType.LONG) || this.equals(DataType.DOUBLE) || this.equals(DataType.INT32) || this.equals(DataType.INT64) || this.equals(DataType.NUMBER))
			isNumber=true;
		return isNumber;
	}
}
