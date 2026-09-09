package fumi.day.literalfilm.render

import android.opengl.GLES30

class ShaderProgram(vertexSrc: String, fragmentSrc: String) {

    val programId: Int = linkProgram(compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc), compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc))

    private val uniformCache = mutableMapOf<String, Int>()

    fun use() {
        GLES30.glUseProgram(programId)
    }

    fun uniformLocation(name: String): Int = uniformCache.getOrPut(name) { GLES30.glGetUniformLocation(programId, name) }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)

        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Shader compile failed: $log")
        }
        return shader
    }

    private fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            error("Program link failed: $log")
        }

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        return program
    }
}
