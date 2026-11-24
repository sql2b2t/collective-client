package me.collective.client.settings

import me.collective.client.ClientContext
import me.collective.client.NamedFeature
import me.collective.client.config.EnumJsonConverter
import java.util.Objects
import java.util.function.Predicate

abstract class Setting<T> : NamedFeature, ValueProvider<T>, JsonConfigurable {
    @JvmField
    var expanded: Boolean = false

    @JvmField
    var visibilityDependency: Boolean = false

    @JvmField
    var opaqueColor: Boolean = false

    @JvmField
    var groupOnly: Boolean = false

    protected var _defaultValue: T
    var visibilityPredicate: Predicate<in T>? = null
    protected var _configName: String = ""
    protected var _boundaryLabel: String = ""

    @JvmField
    protected var changeCallback: Runnable? = null

    private var _value: T
    private var stagedValue: T
    @JvmField
    var minimumValue: T? = null

    @JvmField
    var maximumValue: T? = null

    private var previousValue: T
    private var numericBoundsEnabled: Boolean = false
    protected var _excludedFromConfig: Boolean = false
    private var boundaryLabelCondition: SettingLabelCondition? = null

    var value: T
        @JvmName("getVal") get() = getValue()
        @JvmName("setVal") set(newValue) = setValue(newValue)

    constructor(
        name: String,
        defaultValue: T,
        minimumValue: T?,
        maximumValue: T?,
        visibilityPredicate: Predicate<in T>?
    ) : super(name) {
        this._configName = this.name
        this._boundaryLabel = ""
        this._defaultValue = defaultValue
        this.minimumValue = minimumValue
        this.maximumValue = maximumValue
        this.visibilityPredicate = visibilityPredicate
        this._value = defaultValue
        this.stagedValue = defaultValue
        this.previousValue = defaultValue
        this.numericBoundsEnabled = true
    }

    constructor(
        name: String,
        defaultValue: T,
        visibilityPredicate: Predicate<in T>?
    ) : super(name) {
        this._configName = this.name
        this._boundaryLabel = ""
        this._defaultValue = defaultValue
        this.visibilityPredicate = visibilityPredicate
        this._value = defaultValue
        this.stagedValue = defaultValue
        this.previousValue = defaultValue
        this.numericBoundsEnabled = false
    }

    constructor(name: String, defaultValue: T) : this(name, defaultValue, null as Predicate<in T>?)

    constructor(
        name: String,
        defaultValue: T,
        minimumValue: T?,
        maximumValue: T?
    ) : this(name, defaultValue, minimumValue, maximumValue, null)

    override fun getValue(): T = this._value

    open fun setValue(newValue: T) {
        this.setStagedValue(newValue)
        if (this.numericBoundsEnabled) {
            val minimum = this.minimumValue as? Number
            val requestedMinimum = newValue as? Number
            if (minimum != null && requestedMinimum != null && minimum.toFloat() > requestedMinimum.toFloat()) {
                val lowerBound = this.minimumValue
                if (lowerBound != null) {
                    this.setStagedValue(lowerBound)
                }
            }

            val maximum = this.maximumValue as? Number
            val requestedMaximum = newValue as? Number
            if (maximum != null && requestedMaximum != null && maximum.toFloat() < requestedMaximum.toFloat()) {
                val upperBound = this.maximumValue
                if (upperBound != null) {
                    this.setStagedValue(upperBound)
                }
            }
        }

        this._value = this.stagedValue
        // Preserve reference-change notifications, including newly boxed numeric values.
        if (this._value !== this.previousValue) {
            this.runChangeCallback()
        }

        this.previousValue = this._value
    }

    open fun reset() {
        this.setValue(this._defaultValue)
    }

    val isAtMaximum: Boolean
        @JvmName("isAtMaximumProp") get() = isAtMaximum()

    fun isAtMaximum(): Boolean {
        val maximum = this.getMaximumValue()
        return maximum == this.getValue()
    }

    val isAtMinimum: Boolean
        @JvmName("isAtMinimumProp") get() = isAtMinimum()

    fun isAtMinimum(): Boolean {
        val minimum = this.getMinimumValue()
        return minimum == this.getValue()
    }

    open fun markVisibilityDependency(): Setting<T> {
        this.visibilityDependency = true
        return this
    }

    open fun markAsGroup(): Setting<T> {
        this.groupOnly = true
        this.markVisibilityDependency()
        return this
    }

    open fun dependsOn(vararg dependencies: Setting<*>?): Setting<T> {
        val dependencyVisibility: Predicate<T> = Predicate { _ ->
            for (dependency in dependencies) {
                if (dependency == null) continue
                if (dependency.groupOnly || dependency.visibilityDependency) {
                    if (!dependency.isExpanded()) {
                        return@Predicate false
                    }
                    if (!dependency.isVisible()) {
                        return@Predicate false
                    }
                } else {
                    val dependencyValue = dependency.getValue() as? Boolean
                    if (dependencyValue != true) {
                        return@Predicate false
                    }
                }
            }
            true
        }
        this.addVisibilityPredicate(dependencyVisibility)
        return this
    }

    open fun setBoundaryLabel(label: String, condition: SettingLabelCondition): Setting<T> {
        this._boundaryLabel = label
        this.boundaryLabelCondition = condition
        return this
    }

    val boundaryLabel: String @JvmName("boundaryLabelProp") get() = this._boundaryLabel

    fun getBoundaryLabel(): String = this._boundaryLabel

    fun shouldDisplayBoundaryLabel(): Boolean {
        if (this.hasNumericValue() && this._boundaryLabel.isNotEmpty()) {
            return this.boundaryLabelCondition?.matches(this) == true
        }
        return false
    }

    fun hideWithoutBaritone() {
        val baritoneAvailable: Predicate<T> = Predicate { _ -> ClientContext.baritoneBridge.isAvailable() }
        this.addVisibilityPredicate(baritoneAvailable)
    }

    open fun forceOpaqueColor(): Setting<T> {
        this.opaqueColor = true
        return this
    }

    fun runChangeCallback() {
        if (this.changeCallback != null) {
            try {
                this.changeCallback?.run()
            } catch (_: Throwable) {
            }
        }
    }

    val isGroupOnly: Boolean
        @JvmName("isGroupOnlyProp") get() = isGroupOnly()

    fun isGroupOnly(): Boolean = this.groupOnly

    val isExcludedFromConfig: Boolean
        @JvmName("isExcludedFromConfigProp") get() = isExcludedFromConfig()

    fun isExcludedFromConfig(): Boolean = this._excludedFromConfig
    fun setExcludedFromConfig(excluded: Boolean) {
        this._excludedFromConfig = excluded
    }

    val isExpanded: Boolean
        @JvmName("isExpandedProp") get() = isExpanded()

    fun isExpanded(): Boolean = this.expanded && this.visibilityDependency

    val isVisible: Boolean
        @JvmName("isVisibleProp") get() = isVisible()

    fun isVisible(): Boolean {
        val predicate = this.visibilityPredicate ?: return true
        return predicate.test(this.getValue())
    }

    var configName: String
        @JvmName("getConfigNameProp") get() = getConfigName()
        @JvmName("setConfigNameProp") set(v) = setConfigName(v)

    override fun getConfigName(): String = this._configName

    fun setConfigName(configName: String) {
        this._configName = configName
    }

    fun isNumericBoundsEnabled(): Boolean = this.numericBoundsEnabled

    fun disableNumericBounds() {
        this.numericBoundsEnabled = false
    }

    fun hasNumericValue(): Boolean = this._value is Number

    fun setChangeCallback(callback: Runnable?) {
        this.changeCallback = callback
    }

    fun getStagedValue(): T = this.stagedValue

    fun setStagedValue(stagedValue: T) {
        this.stagedValue = stagedValue
    }

    fun getPreviousValue(): T = this.previousValue

    fun getStagedEnumDisplayName(): String {
        val stagedChoice = this.stagedValue as Enum<*>
        return EnumJsonConverter.getName(stagedChoice)
    }

    fun hasEnumValue(): Boolean = this._value is Enum<*>

    fun hasStringValue(): Boolean = this._value is String

    fun getValueTypeName(candidate: Any): String = candidate.javaClass.simpleName

    fun getMinimumValue(): T? = this.minimumValue

    fun getMaximumValue(): T? = this.maximumValue

    var defaultValue: T
        @JvmName("getDefaultValueProp") get() = getDefaultValue()
        @JvmName("setDefaultValueProp") set(v) = setDefaultValue(v)

    fun getDefaultValue(): T = this._defaultValue

    fun setDefaultValue(defaultValue: T) {
        this._defaultValue = defaultValue
    }

    fun addVisibilityPredicate(predicate: Predicate<in T>) {
        if (this.visibilityPredicate == null) {
            this.visibilityPredicate = predicate
        } else {
            val previous = this.visibilityPredicate!!
            this.visibilityPredicate = Predicate { value -> previous.test(value) && predicate.test(value) }
        }
    }

    abstract fun parseValue(serializedValue: String)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this.javaClass != other.javaClass) return false
        if (!super.equals(other)) return false
        val otherSetting = other as Setting<*>
        return Objects.equals(this._configName, otherSetting._configName)
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), this._configName)
    }
}
