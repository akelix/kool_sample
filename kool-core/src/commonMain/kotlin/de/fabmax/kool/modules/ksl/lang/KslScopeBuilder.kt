package de.fabmax.kool.modules.ksl.lang

import de.fabmax.kool.math.*
import de.fabmax.kool.modules.ksl.model.KslOp
import de.fabmax.kool.modules.ksl.model.KslScope

class KslScopeBuilder(parentOp: KslOp?, val parentScope: KslScopeBuilder?, val parentStage: KslShaderStage) : KslScope(parentOp) {

    inline fun <reified T: Any> findParentOpByType(): T? {
        var parent = parentOp
        while (parent !is T && parent != null) {
            parent = parent.parentScope.parentOp
        }
        return parent as? T
    }

    val isInLoop: Boolean
        get() = findParentOpByType<KslLoop>() != null

    val isInFunction: Boolean
        get() = findParentOpByType<KslFunction<*>.BodyOp>() != null

    fun nextName(prefix: String): String = parentStage.program.nextName(prefix)

    fun getBlocks(name: String?, result: MutableList<KslBlock>): MutableList<KslBlock> {
        ops.forEach { op ->
            if (op is KslBlock && (name == null || op.opName == name)) {
                result += op
            }
            op.childScopes.asSequence().filterIsInstance<KslScopeBuilder>().forEach {
                it.getBlocks(name, result)
            }
        }
        return result
    }

    val Float.const: KslConstFloat1
        get() = KslConstFloat1(this)
    val Int.const: KslConstInt1
        get() = KslConstInt1(this)
    val Boolean.const: KslConstBool1
        get() = KslConstBool1(this)

    val Vec2f.const: KslConstFloat2
        get() = constFloat2(x, y)
    val Vec3f.const: KslConstFloat3
        get() = constFloat3(x, y, z)
    val Vec4f.const: KslConstFloat4
        get() = constFloat4(x, y, z, w)

    val Vec2i.const: KslConstInt2
        get() = constInt2(x, y)
    val Vec3i.const: KslConstInt3
        get() = constInt3(x, y, z)
    val Vec4i.const: KslConstInt4
        get() = constInt4(x, y, z, w)

    fun constFloat2(x: Float, y: Float) = KslConstFloat2(x, y)
    fun constFloat2(x: KslExpression<KslTypeFloat1>, y: KslExpression<KslTypeFloat1>) = KslConstFloat2(x, y)

    fun constFloat3(x: Float, y: Float, z: Float) = KslConstFloat3(x, y, z)
    fun constFloat3(x: KslExpression<KslTypeFloat1>, y: KslExpression<KslTypeFloat1>, z: KslExpression<KslTypeFloat1>) =
        KslConstFloat3(x, y, z)

    fun constFloat4(x: Float, y: Float, z: Float, w: Float) = KslConstFloat4(x, y, z, w)
    fun constFloat4(x: KslExpression<KslTypeFloat1>, y: KslExpression<KslTypeFloat1>, z: KslExpression<KslTypeFloat1>, w: KslExpression<KslTypeFloat1>) =
        KslConstFloat4(x, y, z, w)
    fun constFloat4(xyz: KslVectorExpression<KslTypeFloat3, KslTypeFloat1>, w: Float) =
        constFloat4(xyz, w.const)
    fun constFloat4(xyz: KslVectorExpression<KslTypeFloat3, KslTypeFloat1>, w: KslExpression<KslTypeFloat1>) =
        KslConstFloat4(xyz.x, xyz.y, xyz.z, w)

    fun constInt2(x: Int, y: Int) = KslConstInt2(x, y)
    fun constInt2(x: KslExpression<KslTypeInt1>, y: KslExpression<KslTypeInt1>) = KslConstInt2(x, y)

    fun constInt3(x: Int, y: Int, z: Int) = KslConstInt3(x, y, z)
    fun constInt3(x: KslExpression<KslTypeInt1>, y: KslExpression<KslTypeInt1>, z: KslExpression<KslTypeInt1>) =
        KslConstInt3(x, y, z)

    fun constInt4(x: Int, y: Int, z: Int, w: Int) = KslConstInt4(x, y, z, w)
    fun constInt4(x: KslExpression<KslTypeInt1>, y: KslExpression<KslTypeInt1>, z: KslExpression<KslTypeInt1>, w: KslExpression<KslTypeInt1>) =
        KslConstInt4(x, y, z, w)
    fun constInt4(xyz: KslVectorExpression<KslTypeInt3, KslTypeInt1>, w: Int) =
        constInt4(xyz, w.const)
    fun constInt4(xyz: KslVectorExpression<KslTypeInt3, KslTypeInt1>, w: KslExpression<KslTypeInt1>) =
        KslConstInt4(xyz.x, xyz.y, xyz.z, w)

    fun floatVar(initValue: KslScalarExpression<KslTypeFloat1>? = null, name: String? = null) =
        KslVarScalar(name ?: nextName("f1"), KslTypeFloat1, true).also {
            ops += KslDeclareVar(it, initValue, this)
        }
    fun float2Var(initValue: KslVectorExpression<KslTypeFloat2, KslTypeFloat1>? = null, name: String? = null) =
        KslVarVector(name ?: nextName("f2"), KslTypeFloat2, true).also {
            ops += KslDeclareVar(it, initValue, this)
        }
    fun float3Var(initValue: KslVectorExpression<KslTypeFloat3, KslTypeFloat1>? = null, name: String? = null) =
        KslVarVector(name ?: nextName("f3"), KslTypeFloat3, true).also {
            ops += KslDeclareVar(it, initValue, this)
        }
    fun float4Var(initValue: KslVectorExpression<KslTypeFloat4, KslTypeFloat1>? = null, name: String? = null) =
        KslVarVector(name ?: nextName("f4"), KslTypeFloat4, true).also {
            ops += KslDeclareVar(it, initValue, this)
        }

    fun floatArray(arraySize: Int, name: String? = null) = floatArray(arraySize.const, name)
    fun floatArray(arraySize: KslExpression<KslTypeInt1>, name: String?) =
        KslArrayScalar(name ?: nextName("floatArray"), KslTypeFloat1, arraySize, true).also { definedStates += it }

    fun intVar(initValue: KslScalarExpression<KslTypeInt1>? = null, name: String? = null) =
        KslVarScalar(name ?: nextName("i1"), KslTypeInt1, true).also {
            ops += KslDeclareVar(it, initValue, this)
        }
    fun int2Var(initValue: KslVectorExpression<KslTypeInt2, KslTypeInt1>? = null, name: String? = null) =
        KslVarVector(name ?: nextName("i2"), KslTypeInt2, true).also {
            ops += KslDeclareVar(it, initValue, this)
        }
    fun int3Var(initValue: KslVectorExpression<KslTypeInt3, KslTypeInt1>? = null, name: String? = null) =
        KslVarVector(name ?: nextName("i3"), KslTypeInt3, true).also {
            ops += KslDeclareVar(it, initValue, this)
        }
    fun int4Var(initValue: KslVectorExpression<KslTypeInt4, KslTypeInt1>? = null, name: String? = null) =
        KslVarVector(name ?: nextName("i4"), KslTypeInt4, true).also {
            ops += KslDeclareVar(it, initValue, this)
        }

    fun mat2Var(initValue: KslMatrixExpression<KslTypeMat2, KslTypeFloat2>? = null, name: String? = null) =
        KslVarMatrix(name ?: nextName("m2"), KslTypeMat2, true).also {
            ops += KslDeclareVar(it, initValue, this)
        }
    fun mat3Var(initValue: KslMatrixExpression<KslTypeMat3, KslTypeFloat3>? = null, name: String? = null) =
        KslVarMatrix(name ?: nextName("m3"), KslTypeMat3, true).also {
            ops += KslDeclareVar(it, initValue, this)
        }
    fun mat4Var(initValue: KslMatrixExpression<KslTypeMat4, KslTypeFloat4>? = null, name: String? = null) =
        KslVarMatrix(name ?: nextName("m4"), KslTypeMat4, true).also {
            ops += KslDeclareVar(it, initValue, this)
        }

    infix fun <T: KslType> KslAssignable<T>.set(expression: KslExpression<T>) {
        ops += KslAssign(this, expression, this@KslScopeBuilder)
    }

    fun `if`(condition: KslExpression<KslTypeBool1>, block: KslScopeBuilder.() -> Unit): KslIf {
        val stmt = KslIf(condition, this).apply { body.block() }
        ops += stmt
        return stmt
    }

    fun fori(fromInclusive: KslScalarExpression<KslTypeInt1>, toExclusive: KslScalarExpression<KslTypeInt1>,
             block: KslScopeBuilder.(KslScalarExpression<KslTypeInt1>) -> Unit) {
        val i = intVar(fromInclusive)
        `for`(i, i lt toExclusive, 1.const, block)
    }

    fun <T> `for`(loopVar: KslVarScalar<T>, whileExpr: KslScalarExpression<KslTypeBool1>,
                  incExpr: KslScalarExpression<T>, block: KslScopeBuilder.(KslScalarExpression<T>) -> Unit)
            where T: KslNumericType, T: KslScalar {
        val loop = KslLoopFor(loopVar, whileExpr, incExpr, this).apply { body.block(loopVar) }
        ops += loop
    }

    fun `break`() {
        ops += KslLoopBreak(this)
    }

    fun `continue`() {
        ops += KslLoopContinue(this)
    }

    fun discard() {
        ops += KslDiscard(this)
    }

    fun <T> any(boolVec: KslVectorExpression<T, KslTypeBool1>) where T: KslBoolType, T: KslVector<KslTypeBool1> =
        KslBoolVectorExpr(boolVec, KslBoolVecOperator.Any)
    fun <T> all(boolVec: KslVectorExpression<T, KslTypeBool1>) where T: KslBoolType, T: KslVector<KslTypeBool1> =
        KslBoolVectorExpr(boolVec, KslBoolVecOperator.All)

    operator fun <T: KslType> KslAssignable<T>.plusAssign(expr: KslExpression<T>) {
        ops += KslAugmentedAssign(this, KslMathOperator.Plus, expr, this@KslScopeBuilder)
    }

    operator fun <T: KslType> KslAssignable<T>.minusAssign(expr: KslExpression<T>) {
        ops += KslAugmentedAssign(this, KslMathOperator.Minus, expr, this@KslScopeBuilder)
    }

    operator fun <T: KslType> KslAssignable<T>.timesAssign(expr: KslExpression<T>) {
        ops += KslAugmentedAssign(this, KslMathOperator.Times, expr, this@KslScopeBuilder)
    }

    operator fun <T: KslType> KslAssignable<T>.divAssign(expr: KslExpression<T>) {
        ops += KslAugmentedAssign(this, KslMathOperator.Divide, expr, this@KslScopeBuilder)
    }

    operator fun <T: KslType> KslAssignable<T>.remAssign(expr: KslExpression<T>) {
        ops += KslAugmentedAssign(this, KslMathOperator.Remainder, expr, this@KslScopeBuilder)
    }

    // builtin functions
    fun <S> clamp(value: KslScalarExpression<S>, min: KslScalarExpression<S>, max: KslScalarExpression<S>)
        where S: KslNumericType, S: KslScalar = KslBuiltinClampScalar(value, min, max)
    fun <V, S> clamp(vec: KslVectorExpression<V, S>, min: KslVectorExpression<V, S>, max: KslVectorExpression<V, S>)
        where V: KslNumericType, V: KslVector<S>, S: KslNumericType, S: KslScalar = KslBuiltinClampVector(vec, min, max)

    fun <S> cos(value: KslScalarExpression<S>) where S: KslFloatType, S: KslScalar = KslBuiltinCosScalar(value)
    fun <V, S> cos(vec: KslVectorExpression<V, S>)
        where V: KslFloatType, V: KslVector<S>, S: KslFloatType, S: KslScalar = KslBuiltinCosVector(vec)

    fun <V, S> dot(vec1: KslVectorExpression<V, S>, vec2: KslVectorExpression<V, S>)
        where V: KslFloatType, V: KslVector<S>, S: KslFloatType, S: KslScalar = KslBuiltinDot(vec1, vec2)

    fun <V, S> length(arg: KslVectorExpression<V, S>) where V: KslFloatType, V: KslVector<S>, S: KslFloatType, S: KslScalar = KslBuiltinLength(arg)

    fun <S> max(a: KslScalarExpression<S>, b: KslScalarExpression<S>) where S: KslFloatType, S: KslScalar = KslBuiltinMaxScalar(a, b)
    fun <V, S> max(a: KslVectorExpression<V, S>, b: KslVectorExpression<V, S>)
        where V: KslFloatType, V: KslVector<S>, S: KslFloatType, S: KslScalar = KslBuiltinMaxVector(a, b)

    fun <S> min(a: KslScalarExpression<S>, b: KslScalarExpression<S>) where S: KslFloatType, S: KslScalar = KslBuiltinMinScalar(a, b)
    fun <V, S> min(a: KslVectorExpression<V, S>, b: KslVectorExpression<V, S>)
        where V: KslFloatType, V: KslVector<S>, S: KslFloatType, S: KslScalar = KslBuiltinMinVector(a, b)

    fun mix(x: KslScalarExpression<KslTypeFloat1>, y: KslScalarExpression<KslTypeFloat1>, a: KslScalarExpression<KslTypeFloat1>) =
        KslBuiltinMixScalar(x, y, a)
    fun <V, S> mix(x: KslVectorExpression<V, S>, y: KslVectorExpression<V, S>, a: KslVectorExpression<V, S>)
        where V: KslFloatType, V: KslVector<S>, S: KslFloatType, S: KslScalar = KslBuiltinMixVector(x, y, a)
    fun <V, S> mix(x: KslVectorExpression<V, S>, y: KslVectorExpression<V, S>, a: KslScalarExpression<S>)
        where V: KslFloatType, V: KslVector<S>, S: KslFloatType, S: KslScalar = KslBuiltinMixVector(x, y, a)

    fun <V, S> normalize(arg: KslVectorExpression<V, S>) where V: KslFloatType, V: KslVector<S>, S: KslFloatType, S: KslScalar = KslBuiltinNormalize(arg)

    fun <V, S> reflect(vec1: KslVectorExpression<V, S>, vec2: KslVectorExpression<V, S>)
        where V: KslFloatType, V: KslVector<S>, S: KslFloatType, S: KslScalar = KslBuiltinReflect(vec1, vec2)

    fun pow(value: KslScalarExpression<KslTypeFloat1>, power: KslScalarExpression<KslTypeFloat1>) = KslBuiltinPowScalar(value, power)
    fun <V, S> pow(vec: KslVectorExpression<V, S>, power: KslVectorExpression<V, S>)
        where V: KslFloatType, V: KslVector<S>, S: KslFloatType, S: KslScalar = KslBuiltinPowVector(vec, power)

    fun <S> sin(value: KslScalarExpression<S>) where S: KslFloatType, S: KslScalar = KslBuiltinSinScalar(value)
    fun <V, S> sin(vec: KslVectorExpression<V, S>)
            where V: KslFloatType, V: KslVector<S>, S: KslFloatType, S: KslScalar = KslBuiltinSinVector(vec)

    fun <S> smoothStep(low: KslScalarExpression<S>, high: KslScalarExpression<S>, x: KslScalarExpression<S>)
            where S: KslFloatType, S: KslScalar = KslBuiltinSmoothStepScalar(low, high, x)
    fun <V, S> smoothStep(low: KslVectorExpression<V, S>, high: KslVectorExpression<V, S>, x: KslVectorExpression<V, S>)
            where V: KslFloatType, V: KslVector<S>, S: KslFloatType, S: KslScalar = KslBuiltinSmoothStepVector(low, high, x)

    // builtin texture functions
    fun <T: KslTypeColorSampler<C>, C: KslFloatType> sampleTexture(sampler: KslExpression<T>, coord: KslExpression<C>) =
        KslSampleColorTexture(sampler, coord)

}