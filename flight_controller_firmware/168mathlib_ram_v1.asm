
; 16.8 signed binary math lib for AVR's with RAM V1.0
; ALL code by Rolf R Bakke,  April 2010

;Uses no AVR registers exept r16


.def	a=r16

.def	Op1_2=r17
.def	Op1_1=r18
.def	Op1_0=r19

.def	Op2_2=r20
.def	Op2_1=r21
.def	Op2_0=r22

.def	Result2=r23
.def	Result1=r24
.def	Result0=r25
.def	Sign=r26

.equ	Op1Index=B16_WorkAdd+0	;Operand #1 index
.equ	Op2Index=B16_WorkAdd+1	;Operand #2 index

.equ	ZaddLow=b16_WorkAdd+2
.equ	ZaddHigh=b16_WorkAdd+3

.equ	b168Temp0=b16_WorkAdd+4
.equ	b168Temp1=b16_WorkAdd+5
.equ	b168Temp2=b16_WorkAdd+6

.equ	RegSave=B16_WorkAdd+7




b16_sr:	;sts RegSave+0,Result0
;	sts RegSave+1,Result1
	sts RegSave+2,Result2

	sts RegSave+3,Op1_0
	sts RegSave+4,Op1_1
	sts RegSave+5,Op1_2

	sts RegSave+6,Op2_0
	sts RegSave+7,Op2_1
	sts RegSave+8,Op2_2

	sts RegSave+9,zl
	sts RegSave+10,zh

;	sts RegSave+11, Sign
	ret

b16_lr:	
;	lds Result0,RegSave+0
;	lds Result1,RegSave+1
	lds Result2,RegSave+2

	lds Op1_0,RegSave+3
	lds Op1_1,RegSave+4
	lds Op1_2,RegSave+5

	lds Op2_0,RegSave+6
	lds Op2_1,RegSave+7
	lds Op2_2,RegSave+8

	lds zl,RegSave+9
	lds zh,RegSave+10
;	lds Sign, RegSave+11

	ret



b16_clr_c:
	rcall b16_getregadd
	clr a
	st z+,a
	st z+,a
	st z+,a
	st z,a
	ret


b16_neg_c:			;negate
	rcall b16_pre2
	mov Op2_0, Op1_0
	mov Op2_1, Op1_1
	mov Op2_2, Op1_2

	clr Op1_0
	clr Op1_1
	clr Op1_2

	sub Op1_0, Op2_0
	sbc Op1_1, Op2_1
	sbc Op1_2, Op2_2

	rcall b16_post1
	ret


b16_cmp_c:			;Only use breq, brne, brge and brlt after the compare  (OK)
	rcall b16_pre1

	cp  Op1_0,Op2_0
	cpc Op1_1,Op2_1
	cpc Op1_2,Op2_2

	ret



b16_move_c:				;(OK)
	lds a,Op2Index
	rcall b16_load_c
	lds a,Op1Index
	rcall b16_store_c
	ret
		

b16_store_c:
	rcall b16_getregadd
	lds a,b168temp2
	st z+,a
	lds a,b168temp1
	st z+,a
	lds a,b168temp0
	st z+,a
	ret


b16_load_c:
	rcall b16_getregadd
	ld a,z+
	sts b168temp2, a
	ld a,z+
	sts b168temp1, a
	ld a,z+
	sts b168temp0, a
	ret





;	-*- 16.8 signed binary addition -*-  (ok)

;	  sum     augend      addend  
;	Result = Operand#1 + Operand#2

b16_add_c:
	rcall b16_pre1

	add Op1_0, Op2_0
	adc Op1_1, Op2_1
	adc Op1_2, Op2_2
	
	rjmp b16_post1




;	-*- 16.8 signed binary subtraction -*- (OK)


;	difference   minuend    subtrahend
;	   Result = Operand#1 - Operand#2


b16_sub_c:
	rcall b16_pre1

	sub Op1_0, Op2_0
	sbc Op1_1, Op2_1
	sbc Op1_2, Op2_2
	
	rjmp b16_post1




;	-*- 16.8 signed binary multiply -*- (OK)

;	product  multiplicand  multiplier
;	Result = Operand#1  *  Operand#2

b16_mul_c:
	rcall b16_pre1

	mov Sign, Op1_2		;calculate result sign
	eor Sign, Op2_2

	tst Op1_2		;Op1=ABS(Op1)
	brpl b16_mu1
	com Op1_0
	com Op1_1
	com Op1_2
	ldi a,1
	add Op1_0, a
	clr a
	adc Op1_1, a
	adc Op1_2, a

b16_mu1:tst Op2_2		;Op2=ABS(Op2)
	brpl b16_mu2
	com Op2_0
	com Op2_1
	com Op2_2
	ldi a,1
	add Op2_0, a
	clr a
	adc Op2_1, a
	adc Op2_2, a

b16_mu2:

	clr Result0
	clr Result1
	clr Result2

	mul Op1_0, Op2_0	;Mul #1
	push r0
	add Result0, r1
	clr a
	adc Result1, a
	adc Result2, a

	mul Op1_0, Op2_1	;mul #2
	add Result0, r0
	adc Result1, r1
	adc Result2, a

	mul Op1_0, Op2_2	;mul #3
	add Result1, r0
	adc Result2, r1

	mul Op1_1, Op2_0	;mul #4
	add Result0, r0
	adc Result1, r1
	adc Result2, a

	mul Op1_1, Op2_1	;mul #5
	add Result1, r0
	adc Result2, r1

	mul Op1_1, Op2_2	;mul #6
	add Result2, r0
	
	mul Op1_2, Op2_0	;mul #7
	add Result1, r0
	adc Result2, r1

	mul Op1_2, Op2_1	;mul #8
	add Result2, r0
		
	pop r0			;round off
	lsl r0

	adc Result0, a
	adc result1, a
	adc result2, a


	tst Sign		;negate result if sign set.
	brpl b16_mu3
	com Result0
	com Result1
	com Result2
	ldi a,1
	add Result0, a
	clr a
	adc Result1, a
	adc Result2, a



b16_mu3:lds zl, ZaddLow
	lds zh, ZaddHigh

	st z+,Result2
	st z+,Result1
	st z+,Result0
	ret





b16_fdiv_c:			;Fast divide by 2n  (OK)
	rcall b16_pre2
		
	lds a,Op2Index	

b16_f0:	asr Op1_2
	ror Op1_1
	ror Op1_0
	dec a
	brne b16_f0

	adc Op1_0, a		;round off
	adc Op1_1, a
	adc Op1_2, a

	rjmp b16_post1


/*

b16_fmul_c:			;Fast multiply by 2n  (OK)
	rcall b16_pre2
	
	lds a,Op2Index	

b16_u0:	lsl Op1_0
	rol Op1_1
	rol Op1_2
	dec a
	brne b16_u0
	
	rjmp b16_post1



*/


;---- subroutines



	



b16_GetOp1:
	ld Op1_2,z+
	ld Op1_1,z+
	ld Op1_0,z+
	ret


b16_GetOp2:
	ld Op2_2,z+
	ld Op2_1,z+
	ld Op2_0,z+
	ret



b16_getregadd:
	ldi zl,low(b16_regadd)
	ldi zh,high(b16_regadd)
	lsl a
	lsl a
	add zl,a
	clr a
	adc zh,a
	ret


b16_pre1:			;common header for add,sub,mul,cmp
	rcall b16_pre2

	lds a,Op2Index
	rcall b16_getregadd
	rcall b16_GetOp2	

	ret


b16_post1:			;common footer for add,sub,fmul,fdiv, neg
	lds zl, ZaddLow
	lds zh, ZaddHigh

	st z+,Op1_2
	st z+,Op1_1
	st z+,Op1_0

	ret


b16_pre2:			;common header for fmul,fdiv,neg
	lds a,Op1Index
	rcall b16_getregadd
	sts ZaddLow, zl
	sts ZaddHigh, zh
	rcall b16_GetOp1	

	ret
