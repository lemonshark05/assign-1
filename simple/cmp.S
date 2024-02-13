    .intel_syntax noprefix

.data
.globl _foo
_foo:
.quad foo

out_of_bounds_msg:
    .string "Out-of-bounds array access."
invalid_array_length_msg:
    .string "Array length requested for allocation is invalid (either too large or negative)."


.text
    .globl foo
foo:

    push rbp
    mov rbp, rsp
    sub rsp, 16
    jmp foo_entry
foo_entry:
    # Jump(None, Local(BbId("epilogue")))
    jmp foo_epilogue

foo_epilogue:

    add rsp, 16
    pop rbp
    ret

    .globl main
main:

    push rbp
    mov rbp, rsp
    sub rsp, 144
    jmp main_entry
main_entry:
    # Mov { dst: Reg(GP(0)), src: Immediate(0) }
    mov qword ptr [rbp + -8], 0
    # Mov { dst: Reg(GP(1)), src: Immediate(0) }
    mov qword ptr [rbp + -16], 0
    # Mov { dst: Reg(GP(2)), src: Immediate(0) }
    mov qword ptr [rbp + -24], 0
    # Mov { dst: Reg(GP(3)), src: Immediate(0) }
    mov qword ptr [rbp + -32], 0
    # Mov { dst: Reg(GP(4)), src: Immediate(0) }
    mov qword ptr [rbp + -40], 0
    # Mov { dst: Reg(GP(5)), src: Immediate(0) }
    mov qword ptr [rbp + -48], 0
    # Mov { dst: Reg(GP(6)), src: Immediate(0) }
    mov qword ptr [rbp + -56], 0
    # Mov { dst: Reg(GP(7)), src: Immediate(0) }
    mov qword ptr [rbp + -64], 0
    # Mov { dst: Reg(GP(8)), src: Immediate(0) }
    mov qword ptr [rbp + -72], 0
    # Mov { dst: Reg(GP(9)), src: Immediate(0) }
    mov qword ptr [rbp + -80], 0
    # Mov { dst: Reg(GP(10)), src: Immediate(0) }
    mov qword ptr [rbp + -88], 0
    # Mov { dst: Reg(GP(11)), src: Immediate(0) }
    mov qword ptr [rbp + -96], 0
    # Mov { dst: Reg(GP(12)), src: Immediate(0) }
    mov qword ptr [rbp + -104], 0
    # Mov { dst: Reg(GP(13)), src: Immediate(0) }
    mov qword ptr [rbp + -112], 0
    # Mov { dst: Reg(GP(14)), src: Immediate(0) }
    mov qword ptr [rbp + -120], 0
    # p = $copy foo
    # Mov { dst: Reg(GP(11)), src: Location(Global { index: 0, offset: 0 }) }
    # Mov { dst: Mem(Rbp, -96), src: Location(Global { index: 0, offset: 0 }) }
    mov r11, qword ptr [rip + _foo + 0]
    mov qword ptr [rbp + -96], r11
    # r = $alloc 1 [_alloc1]
    # Mov { dst: Reg(GP(15)), src: Immediate(1) }
    mov qword ptr [rbp + -128], 1
    # Shift { dir: Right, lhs: Reg(GP(15)), rhs: 32 }
    shr qword ptr [rbp + -128], 31
    shr qword ptr [rbp + -128], 1
    # Cmp { lhs: Reg(GP(15)), rhs: Immediate(0) }
    cmp qword ptr [rbp + -128], 0
    # Jump(Some(NotEqual), Global(BbId(".invalid_array_length")))
    jne .invalid_array_length
    # Mov { dst: Reg(GP(15)), src: Immediate(8) }
    mov qword ptr [rbp + -128], 8
    # Arith { op: Multiply, lhs: Reg(GP(15)), rhs: Immediate(1) }
    mov r11, qword ptr [rbp + -128]
    imul r11, 1
    mov qword ptr [rbp + -128], r11
    # Arith { op: Add, lhs: Reg(GP(15)), rhs: Immediate(8) }
    add qword ptr [rbp + -128], 8
    # Mov { dst: Reg(Physical(Rdi)), src: Location(Reg(GP(15))) }
    mov rdi, qword ptr [rbp + -128]
    # DirectCall(FuncId("_cflat_alloc"))
    call _cflat_alloc
    # Mov { dst: Reg(GP(13)), src: Location(Reg(Physical(Rax))) }
    mov qword ptr [rbp + -112], rax
    # Mov { dst: Mem(GP(13), 0), src: Immediate(1) }
    mov r10, qword ptr [rbp + -112]
    mov qword ptr [r10 + 0], 1
    # Shift { dir: Left, lhs: Mem(GP(13), 0), rhs: 32 }
    mov r10, qword ptr [rbp + -112]
    shl qword ptr [r10 + 0], 31
    shl qword ptr [r10 + 0], 1
    # Arith { op: Add, lhs: Mem(GP(13), 0), rhs: Immediate(134217728) }
    mov r10, qword ptr [rbp + -112]
    add qword ptr [r10 + 0], 134217728
    # Arith { op: Add, lhs: Reg(GP(13)), rhs: Immediate(8) }
    add qword ptr [rbp + -112], 8
    # _t1 = $arith sub 0 2
    # Mov { dst: Reg(GP(0)), src: Immediate(0) }
    mov qword ptr [rbp + -8], 0
    # Arith { op: Subtract, lhs: Reg(GP(0)), rhs: Immediate(2) }
    sub qword ptr [rbp + -8], 2
    # _t2 = $cmp gt x _t1
    # Mov { dst: Reg(Physical(R10)), src: Immediate(0) }
    mov r10, 0
    # Cmp { lhs: Reg(GP(14)), rhs: Location(Reg(GP(0))) }
    mov r11, qword ptr [rbp + -120]
    cmp r11, qword ptr [rbp + -8]
    # Set { cond: Greater, dst: Physical(R10) }
    setg r10b
    # Mov { dst: Reg(GP(3)), src: Location(Reg(Physical(R10))) }
    mov qword ptr [rbp + -32], r10
    # _t3 = $cmp eq p q
    # Mov { dst: Reg(Physical(R10)), src: Immediate(0) }
    mov r10, 0
    # Cmp { lhs: Reg(GP(11)), rhs: Location(Reg(GP(12))) }
    mov r11, qword ptr [rbp + -96]
    cmp r11, qword ptr [rbp + -104]
    # Set { cond: Equal, dst: Physical(R10) }
    sete r10b
    # Mov { dst: Reg(GP(4)), src: Location(Reg(Physical(R10))) }
    mov qword ptr [rbp + -40], r10
    # _t4 = $cmp neq p q
    # Mov { dst: Reg(Physical(R10)), src: Immediate(0) }
    mov r10, 0
    # Cmp { lhs: Reg(GP(11)), rhs: Location(Reg(GP(12))) }
    mov r11, qword ptr [rbp + -96]
    cmp r11, qword ptr [rbp + -104]
    # Set { cond: NotEqual, dst: Physical(R10) }
    setne r10b
    # Mov { dst: Reg(GP(5)), src: Location(Reg(Physical(R10))) }
    mov qword ptr [rbp + -48], r10
    # _t5 = $cmp eq r 0
    # Mov { dst: Reg(Physical(R10)), src: Immediate(0) }
    mov r10, 0
    # Cmp { lhs: Reg(GP(13)), rhs: Immediate(0) }
    cmp qword ptr [rbp + -112], 0
    # Set { cond: Equal, dst: Physical(R10) }
    sete r10b
    # Mov { dst: Reg(GP(6)), src: Location(Reg(Physical(R10))) }
    mov qword ptr [rbp + -56], r10
    # _t6 = $arith mul 2 _t5
    # Mov { dst: Reg(GP(7)), src: Immediate(2) }
    mov qword ptr [rbp + -64], 2
    # Arith { op: Multiply, lhs: Reg(GP(7)), rhs: Location(Reg(GP(6))) }
    mov r11, qword ptr [rbp + -64]
    imul r11, qword ptr [rbp + -56]
    mov qword ptr [rbp + -64], r11
    # _t7 = $arith add _t4 _t6
    # Mov { dst: Reg(GP(8)), src: Location(Reg(GP(5))) }
    # Mov { dst: Mem(Rbp, -72), src: Location(Mem(Rbp, -48)) }
    mov r11, qword ptr [rbp + -48]
    mov qword ptr [rbp + -72], r11
    # Arith { op: Add, lhs: Reg(GP(8)), rhs: Location(Reg(GP(7))) }
    mov r11, qword ptr [rbp + -64]
    add qword ptr [rbp + -72], r11
    # _t8 = $arith mul 2 _t7
    # Mov { dst: Reg(GP(9)), src: Immediate(2) }
    mov qword ptr [rbp + -80], 2
    # Arith { op: Multiply, lhs: Reg(GP(9)), rhs: Location(Reg(GP(8))) }
    mov r11, qword ptr [rbp + -80]
    imul r11, qword ptr [rbp + -72]
    mov qword ptr [rbp + -80], r11
    # _t9 = $arith add _t3 _t8
    # Mov { dst: Reg(GP(10)), src: Location(Reg(GP(4))) }
    # Mov { dst: Mem(Rbp, -88), src: Location(Mem(Rbp, -40)) }
    mov r11, qword ptr [rbp + -40]
    mov qword ptr [rbp + -88], r11
    # Arith { op: Add, lhs: Reg(GP(10)), rhs: Location(Reg(GP(9))) }
    mov r11, qword ptr [rbp + -80]
    add qword ptr [rbp + -88], r11
    # _t10 = $arith mul 2 _t9
    # Mov { dst: Reg(GP(1)), src: Immediate(2) }
    mov qword ptr [rbp + -16], 2
    # Arith { op: Multiply, lhs: Reg(GP(1)), rhs: Location(Reg(GP(10))) }
    mov r11, qword ptr [rbp + -16]
    imul r11, qword ptr [rbp + -88]
    mov qword ptr [rbp + -16], r11
    # _t11 = $arith add _t2 _t10
    # Mov { dst: Reg(GP(2)), src: Location(Reg(GP(3))) }
    # Mov { dst: Mem(Rbp, -24), src: Location(Mem(Rbp, -32)) }
    mov r11, qword ptr [rbp + -32]
    mov qword ptr [rbp + -24], r11
    # Arith { op: Add, lhs: Reg(GP(2)), rhs: Location(Reg(GP(1))) }
    mov r11, qword ptr [rbp + -16]
    add qword ptr [rbp + -24], r11
    # Mov { dst: Reg(Physical(Rax)), src: Location(Reg(GP(2))) }
    mov rax, qword ptr [rbp + -24]
    # Jump(None, Local(BbId("epilogue")))
    jmp main_epilogue

main_epilogue:

    add rsp, 144
    pop rbp
    ret


.out_of_bounds:
    push rdi
    add rsp, -8
    lea rdi, [rip + out_of_bounds_msg]
    call _cflat_panic
.invalid_array_length:
    push rdi
    add rsp, -8
    lea rdi, [rip + invalid_array_length_msg]
    call _cflat_panic

