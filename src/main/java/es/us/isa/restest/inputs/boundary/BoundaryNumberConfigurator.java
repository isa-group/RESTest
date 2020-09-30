package es.us.isa.restest.inputs.boundary;

import java.util.ArrayList;
import java.util.List;

import es.us.isa.restest.util.DataType;

public class BoundaryNumberConfigurator {

	private Double min;
	private Double max;
	private Double delta;
	private DataType type;

	public BoundaryNumberConfigurator() {
		Integer minInt = Integer.MIN_VALUE;
		min = minInt.doubleValue();
		Integer maxInt = Integer.MAX_VALUE;
		max = maxInt.doubleValue();
		delta = 1d;
		type = DataType.INTEGER;
	}

	/**
	 * Constructor
	 * @param min Min value
	 * @param max Max value
	 * @param delta Amount added/subtracted from min/max boundaries
	 * @param type Data type
	 */
	public BoundaryNumberConfigurator(Double min, Double max, Double delta, DataType type) {
		this.min = min;
		this.max = max;
		this.delta = delta;
		this.type = type;
	}

	
	public List<? extends Number> returnValues() {
		List<Double> values = new ArrayList<>();
		List<Long> longValues = null;

		values.add(min);
		values.add(min - delta);
		values.add(min + delta);
		values.add(max);
		values.add(max - delta);
		values.add(max + delta);
		values.add(Math.ceil((max + min) / 2));

		if (type.equals(DataType.INTEGER) || type.equals(DataType.INT32) || type.equals(DataType.INT64)
				|| type.equals(DataType.LONG)) {
			longValues = mapDoubleListToLongList(values);
		}

		return longValues == null ? values : longValues;
	}

	public Double getMin() {
		return min;
	}

	public void setMin(Double min) {
		this.min = min;
	}

	public Double getMax() {
		return max;
	}

	public void setMax(Double max) {
		this.max = max;
	}

	public Double getDelta() {
		return delta;
	}

	public void setDelta(Double delta) {
		this.delta = delta;
	}

	public DataType getType() {
		return type;
	}

	public void setType(DataType type) {
		this.type = type;
	}

	// Necessary function for when datatype is integer-like. Otherwise, string
	// values written in test cases
	// would appear with a decimal part (e.g. "1.0" instead of "1")
	private List<Long> mapDoubleListToLongList(List<Double> doubleValues) {
		List<Long> longValues = new ArrayList<>();

		for (Double doubleValue : doubleValues) {
			longValues.add(doubleValue.longValue());
		}

		return longValues;
	}
}
