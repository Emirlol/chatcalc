@file:Suppress("NOTHING_TO_INLINE")

package ca.rttv.chatcalc.engine.types

import ca.rttv.chatcalc.config.ConfigManager.config
import ca.rttv.chatcalc.engine.astvisitors.PrettyPrinter
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.interleaveWith
import me.ancientri.rimelib.util.text.text
import net.minecraft.text.Text
import net.minecraft.util.StringIdentifiable
import kotlin.reflect.full.companionObjectInstance

sealed interface MathTensor : MathObject, SourceRepresentable, Iterable<Double> {
	override val type: MathType get() = MathType.TENSOR

	val shape: IntArray
	val size: Int
	val rank: Int get() = shape.size
	val radix: Int
	val strides: IntArray // Required for optimized matmul

	/**
	 * Gets a value at a flattened index.
	 */
	fun getFlat(index: Int): Double

	/**
	 * Gets a value at specific coordinates.
	 */
	operator fun get(indices: IntArray): Double

	/**
	 * Returns a sub-tensor at the given index of the first dimension.
	 * Equivalent to `tensor[i]`. Reduces rank by 1.
	 */
	fun getIndex(index: Int): MathTensor

	/**
	 * Reshapes the tensor to a new shape.
	 */
	fun reshape(newShape: IntArray): MathTensor

	/**
	 * Returns a slice of the tensor along the first dimension.
	 * Equivalent to `tensor[start:end:step]`. Preserves rank.
	 */
	fun slice(start: Int, end: Int, step: Int = 1): MathTensor

	// Generic operations for the underlying data. These can be optimized by implementations.
	fun sum(): MathScalar {
		var sum = 0.0
		fastForEach { sum += it }
		return RealScalar(sum, radix)
	}

	fun sumOf(op: (Double) -> Double): MathScalar {
		var sum = 0.0
		fastForEach { sum += op(it) }
		return RealScalar(sum, radix)
	}

	fun mean(): MathScalar {
		var sum = 0.0
		fastForEach { sum += it }
		return RealScalar(if (size == 0) Double.NaN else sum / size, radix)
	}

	fun meanOf(op: (Double) -> Double): MathScalar {
		var sum = 0.0
		fastForEach { sum += op(it) }
		return RealScalar(if (size == 0) Double.NaN else sum / size, radix)
	}

	fun max(): MathScalar {
		var max = Double.NEGATIVE_INFINITY
		fastForEach { max = maxOf(max, it) }
		return RealScalar(max, radix)
	}

	fun maxOf(op: (Double) -> Double): MathScalar {
		var max = Double.NEGATIVE_INFINITY
		fastForEach { max = maxOf(max, op(it)) }
		return RealScalar(max, radix)
	}

	fun min(): MathScalar {
		var min = Double.POSITIVE_INFINITY
		fastForEach { min = minOf(min, it) }
		return RealScalar(min, radix)
	}

	fun minOf(op: (Double) -> Double): MathScalar {
		var min = Double.POSITIVE_INFINITY
		fastForEach { min = minOf(min, op(it)) }
		return RealScalar(min, radix)
	}

	fun reduce(initial: Double = 0.0, op: (acc: Double, next: Double) -> Double): MathScalar {
		var accumulator = initial
		fastForEach { accumulator = op(accumulator, it) }
		return RealScalar(accumulator, radix)
	}

	/**
	 * Returns a flat iterator over the values in the tensor.
	 */
	override fun iterator(): DoubleIterator

	companion object : MathObjectCodecProvider<MathTensor> {
		/**
		 * Implementation-specific inlined for-each to allow fast array iteration where possible.
		 */
		inline fun MathTensor.fastForEach(action: (Double) -> Unit) {
			when (this) {
				is ArrayTensor -> for (element in data) action(element)
				is RangeTensor -> for (i in 0 until size) action(getFlat(i))
			}
		}

		/**
		 * Implementation-specific inlined for-each with index to allow fast array iteration where possible.
		 */
		inline fun MathTensor.fastForEachIndexed(action: (Int, Double) -> Unit) {
			when (this) {
				is ArrayTensor -> for (i in data.indices) action(i, data[i])
				is RangeTensor -> for (i in 0 until size) action(i, getFlat(i))
			}
		}

		private val companionCodec = StringIdentifiable.createBasicCodec {
			MathTensorCodecProvider::class.sealedSubclasses
				.mapNotNull { it.objectInstance }
				.toTypedArray()
		}

		override val mapCodec: MapCodec<MathTensor> = companionCodec.dispatchMap(
			"impl",
			{ tensor -> tensor::class.companionObjectInstance as MathTensorCodecProvider<*> },
			MathTensorCodecProvider<*>::mapCodec
		)
	}
}

private sealed interface MathTensorCodecProvider<T : MathTensor> : MathObjectCodecProvider<T>, StringIdentifiable {
	override val mapCodec: MapCodec<T>
}

data class ArrayTensor(
	val data: DoubleArray,
	override val shape: IntArray,
	override val radix: Int = 10
) : MathTensor {
	override val size: Int get() = data.size

	override val strides: IntArray by lazy { calculateStrides(shape) }

	// Heuristic: It's most likely longer than 255 chars if the size is over 50
	override fun toSourceString(): String? = if (size > 50) null else formatTensorRecursive(0, 0)

	override fun toPrettyText(): Text = formatTensorTextRecursive(0, 0)

	private fun formatTensorRecursive(dimIndex: Int, offset: Int): String {
		// Last tensor dimension, 1D
		if (dimIndex == shape.lastIndex) {
			val length = shape[dimIndex]
			return (0 until length)
				.map { data[it + offset] }
				.joinToString(",", "[", "]") { config.decimalFormat.format(it) }
		}

		// Recursively print slices
		val stride = strides[dimIndex]
		val length = shape[dimIndex]
		val sb = StringBuilder("[")
		for (i in 0 until length) {
			if (i > 0) sb.append(",")
			sb.append(formatTensorRecursive(dimIndex + 1, offset + i * stride))
		}
		sb.append("]")
		return sb.toString()
	}

	private fun formatTensorTextRecursive(dimIndex: Int, offset: Int): Text {
		val length = shape[dimIndex]
		val isLastDim = dimIndex == shape.lastIndex

		fun formatElement(i: Int): Text {
			return if (isLastDim) {
				config.decimalFormat.format(data[offset + i]).text(ColorPalette.PEACH)
			} else {
				formatTensorTextRecursive(dimIndex + 1, offset + i * strides[dimIndex])
			}
		}

		return text {
			"[" colored ColorPalette.SUBTEXT2
			if (length > 10) {
				// First 5
				(0 until 5).asSequence()
					.map(::formatElement)
					.interleaveWith(PrettyPrinter.COMMA)
					.forEach(::append)

				+PrettyPrinter.COMMA
				if (isLastDim) "...${length - 10} more..." colored ColorPalette.SUBTEXT2
				else "..." colored ColorPalette.SUBTEXT2
				+PrettyPrinter.COMMA

				// Last 5
				((length - 5) until length).asSequence()
					.map(::formatElement)
					.interleaveWith(PrettyPrinter.COMMA)
					.forEach(::append)
			} else {
				// Print all
				(0 until length).asSequence()
					.map(::formatElement)
					.interleaveWith(PrettyPrinter.COMMA)
					.forEach(::append)
			}
			"]" colored ColorPalette.SUBTEXT2
		}
	}

	/**
	 * Gets the value at the given index.
	 * @return the value at the given index.
	 * @throws IndexOutOfBoundsException if the index is out of bounds.
	 */
	override fun getFlat(index: Int): Double = data[index]

	/**
	 * Gets the value at the given indices.
	 * @param indices the indices to get the value at.
	 * @throws IllegalArgumentException if the number of indices does not match the tensor rank.
	 * @return the value at the given indices.
	 */
	override operator fun get(indices: IntArray): Double {
		require(indices.size == rank) { "Expected $rank indices, got ${indices.size}." }
		var flatIndex = 0
		for (i in indices.indices) flatIndex += indices[i] * strides[i]
		return data[flatIndex]
	}

	override fun getIndex(index: Int): MathTensor {
		require(index in 0 until shape[0])
		if (rank == 1) return ArrayTensor(doubleArrayOf(data[index]), intArrayOf(), radix)

		val subSize = strides[0]
		val offset = index * subSize

		val newData = DoubleArray(subSize)
		data.copyInto(newData, 0, offset, offset + subSize)
		val newShape = shape.copyOfRange(1, shape.size)

		return ArrayTensor(newData, newShape, radix)
	}

	override fun reshape(newShape: IntArray): MathTensor {
		val newSize = newShape.reduce(Int::times)
		require(newSize == size) { "Cannot reshape tensor of size $size to shape $newShape." }
		return copy(shape = newShape)
	}

	override fun slice(start: Int, end: Int, step: Int): MathTensor {
		val actualStart = start.coerceIn(0, shape[0])
		val actualEnd = end.coerceIn(0, shape[0])

		val dim0Size = ((actualEnd - actualStart + step - 1) / step).coerceAtLeast(0)

		val newShape = shape.clone().apply { this[0] = dim0Size }

		if (step == 1) { // Array copy when contiguous
			val subBlockSize = strides[0]
			val totalSize = dim0Size * subBlockSize
			val offset = actualStart * subBlockSize
			val newData = DoubleArray(totalSize)
			data.copyInto(newData, 0, offset, offset + totalSize)
			return ArrayTensor(newData, newShape, radix)
		}

		// Manual copy when strided
		val subBlockSize = strides[0]
		val newData = DoubleArray(dim0Size * subBlockSize)
		var targetIndex = 0
		for (i in actualStart until actualEnd step step) {
			val srcOffset = i * subBlockSize
			data.copyInto(newData, targetIndex, srcOffset, srcOffset + subBlockSize)
			targetIndex += subBlockSize
		}

		return ArrayTensor(newData, newShape, radix)
	}

	override fun sum(): RealScalar = RealScalar(data.sum(), radix)

	override fun sumOf(op: (Double) -> Double): RealScalar {
		var sum = 0.0
		for (element in data) sum += op(element)
		return RealScalar(sum, radix)
	}

	override fun reduce(initial: Double, op: (acc: Double, next: Double) -> Double): RealScalar {
		var accumulator = initial
		for (element in data) accumulator = op(accumulator, element)
		return RealScalar(accumulator, radix)
	}

	override fun iterator(): DoubleIterator = data.iterator()

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is ArrayTensor) return false

		if (!data.contentEquals(other.data)) return false
		if (!shape.contentEquals(other.shape)) return false

		return true
	}

	override fun hashCode(): Int {
		var result = data.contentHashCode()
		result = 31 * result + shape.contentHashCode()
		return result
	}

	companion object : MathTensorCodecProvider<ArrayTensor> {
		/**
		 * Creates a 1-D tensor with the given values.
		 * @param values the values to put in the tensor.
		 * @return the created tensor.
		 */
		@JvmName("vectorVarargs")
		inline fun vector(vararg values: Double, radix: Int = 10) = vector(values, radix)

		/**
		 * Creates a 1-D tensor with the given values.
		 * @param values the values to put in the tensor.
		 * @return the created tensor.
		 */
		inline fun vector(values: DoubleArray, radix: Int = 10) = ArrayTensor(values, intArrayOf(values.size), radix)

		override val mapCodec: MapCodec<ArrayTensor> = RecordCodecBuilder.mapCodec { instance ->
			instance.group(
				Codec.DOUBLE.listOf()
					.xmap({ it.toDoubleArray() }, { it.toList() })
					.fieldOf("data").forGetter(ArrayTensor::data),
				Codec.INT.listOf()
					.xmap({ it.toIntArray() }, { it.toList() })
					.fieldOf("shape").forGetter(ArrayTensor::shape),
				Codec.INT.fieldOf("radix").forGetter(ArrayTensor::radix)
			).apply(instance, ::ArrayTensor)
		}

		override fun asString(): String = "array"
	}
}

data class RangeTensor(
	val start: Double,
	val step: Double,
	override val shape: IntArray,
	override val radix: Int = 10
) : MathTensor {
	override val size: Int = shape.reduce { acc, i -> acc * i }
	override val strides by lazy { calculateStrides(shape) }

	@Suppress("OVERRIDE_BY_INLINE") // Skip the function call overhead in fastForEach since these are all public values. It's just logic we're inlining.
	override inline fun getFlat(index: Int): Double = start + (index * step)

	override operator fun get(indices: IntArray): Double {
		var flatIndex = 0
		for (i in indices.indices) flatIndex += indices[i] * strides[i]
		return getFlat(flatIndex)
	}

	override fun getIndex(index: Int): MathTensor {
		require(rank > 0) { "Cannot get index of rank 0 tensor." }
		require(index in 0..<shape[0]) { "Index $index out of bounds for tensor of shape ${shape.contentToString()}." }
		if (rank == 1) return RangeTensor(getFlat(index), step, intArrayOf(), radix)

		val offset = index * strides[0]
		val newStart = start + (offset * step)
		val newShape = shape.copyOfRange(1, shape.size)

		return RangeTensor(newStart, step, newShape, radix)
	}

	override fun sum(): MathScalar {
		// Optimized sum function for ranges
		val n = size.toDouble()
		val last = start + (step * (n - 1))
		val sum = n * (start + last) / 2.0 // Average of the range * size
		return RealScalar(sum, radix)
	}

	override fun mean(): MathScalar {
		val midpoint = step * (size - 1) / 2.0
		return RealScalar(start + midpoint, radix)
	}

	override fun max(): MathScalar {
		val last = start + (step * (size - 1))
		return RealScalar(last, radix)
	}

	override fun min(): MathScalar = RealScalar(start, radix)

	override fun iterator(): DoubleIterator = object : DoubleIterator() {
		private var currentIndex = 0
		override fun hasNext(): Boolean = currentIndex < size
		override fun nextDouble(): Double = getFlat(currentIndex++)
	}

	override fun toSourceString(): String = "range($start, $step, ${shape.contentToString()})       "

	override fun toPrettyText(): Text = if (size > 50 && rank == 1) text {
		"[" colored ColorPalette.SUBTEXT2
		// First 5
		(0..<5).asSequence()
			.map { prettyFormatValue(getFlat(it), radix).text(ColorPalette.PEACH) }
			.interleaveWith(PrettyPrinter.COMMA)
			.forEach(::append)

		+PrettyPrinter.COMMA
		"...${size - 10} more..." colored ColorPalette.SUBTEXT2
		+PrettyPrinter.COMMA

		// Last 5
		((size - 5) until size).asSequence()
			.map { prettyFormatValue(getFlat(it), radix).text(ColorPalette.PEACH) }
			.interleaveWith(PrettyPrinter.COMMA)
			.forEach(::append)
		"]" colored ColorPalette.SUBTEXT2
	} else toArrayTensor().toPrettyText() // Fallback to ArrayTensor for pretty printing if too small or not 1D

	fun toArrayTensor() = ArrayTensor(DoubleArray(size) { getFlat(it) }, shape, radix)

	override fun reshape(newShape: IntArray): MathTensor {
		val newSize = newShape.reduce { acc, i -> acc * i }
		require(newSize == size) { "Cannot reshape size $size into shape ${newShape.contentToString()}" }

		return copy(shape = newShape)
	}

	override fun slice(start: Int, end: Int, step: Int): MathTensor {
		require(rank > 0) { "Cannot slice rank 0 tensor." }
		val actualStart = start.coerceIn(0, shape[0])
		val actualEnd = end.coerceIn(0, shape[0])
		val sliceSize = ((actualEnd - actualStart + step - 1) / step).coerceAtLeast(0)
		if (rank == 1) {
			val newStart = this.start + (actualStart * this.step)
			val newStep = this.step * step
			return RangeTensor(newStart, newStep, intArrayOf(sliceSize), radix)
		}

		if (step == 1) {
			val offset = actualStart * strides[0]
			val newStart = this.start + (offset * this.step)
			val newShape = shape.clone().apply { this[0] = sliceSize }
			return RangeTensor(newStart, this.step, newShape, radix)
		}

		// Strided slice of multidimensional tensor creates gaps in memory, we have to fall back to array tensor
		return toArrayTensor().slice(actualStart, actualEnd, step)
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is RangeTensor) return false

		if (start != other.start) return false
		if (step != other.step) return false
		if (radix != other.radix) return false
		if (size != other.size) return false
		if (!shape.contentEquals(other.shape)) return false
		if (!strides.contentEquals(other.strides)) return false

		return true
	}

	override fun hashCode(): Int {
		var result = start.hashCode()
		result = 31 * result + step.hashCode()
		result = 31 * result + radix
		result = 31 * result + size
		result = 31 * result + shape.contentHashCode()
		result = 31 * result + strides.contentHashCode()
		return result
	}

	companion object : MathTensorCodecProvider<RangeTensor> {
		override val mapCodec: MapCodec<RangeTensor> = RecordCodecBuilder.mapCodec { instance ->
			instance.group(
				Codec.DOUBLE.fieldOf("start").forGetter(RangeTensor::start),
				Codec.DOUBLE.fieldOf("step").forGetter(RangeTensor::step),
				Codec.INT.listOf()
					.xmap({ it.toIntArray() }, { it.toList() })
					.fieldOf("shape").forGetter(RangeTensor::shape),
				Codec.INT.fieldOf("radix").forGetter(RangeTensor::radix)
			).apply(instance, ::RangeTensor)
		}

		fun vector(start: Double, step: Double) = RangeTensor(start, step, intArrayOf(1))

		const val MAX_LENGTH = 250_000_000

		override fun asString(): String = "range"
	}
}

fun calculateStrides(shape: IntArray): IntArray {
	val strides = IntArray(shape.size)
	var acc = 1
	for (i in shape.lastIndex downTo 0) {
		strides[i] = acc
		acc *= shape[i]
	}
	return strides
}