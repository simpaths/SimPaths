package simpaths.model.enums;

public enum Gender {
	Male(true),
	Female(false);

	private final boolean value;
	Gender(boolean value) { this.value = value; }
	public boolean toBoolean() { return value; }

	public static Gender of(boolean b) { return b ? Male : Female; }
}
