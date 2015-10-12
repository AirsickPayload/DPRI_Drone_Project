
;-*- Quadrocopter (Quadrotor) Controller V4.7X-*-
;-*-     All Code And Hardware Design         -*-
;-*-      By Rolf R Bakke,  April, juli 2010  -*-

; (I have not peeked in others projects or code :-)

; Code is best viewed in monospace font and tab = 8


; Added gyro direction reversing.

; Added calibration delay.

; Added potmeter reversing

; Added stick scaling

; Added Yaw command limiting

; Changed gain pot scaling

; Added arming/disarming


; V 4.6:
; fixed the ChannelIsUpdating flag issue, rewrote the math library
; Higher ESC refresh rate, 290Hz
; added PI control

; V 4.7:
; ESC refresh rate: 300Hz
; Added I-term anti wind-up
; Changed Pot control: Roll pot -> Roll/Pitch P-term.  Pitch pot -> Roll/Pitch I-term.

; V 4.7 SC
; Added StickCentre learning

;   View from above

;      Forward


; M1,CW     M3,CCW
;   *         *
;    \       /
;     \    /
;      \ /
;       +
;      / \
;     /    \
;    /       \
;   *         *
; M2,CCW    M4,CW


;******************* SETTINGS **************************

; This one determines the gyro sensitivity.
; Reducing the value by one doubles the sensitivity.
; Increasing the value by one halves the sensitivity.

.equ	ScaleGyro	= 2


; This one determines the stick sensitivity.
; Reducing the value by one doubles the sensitivity.
; Increasing the value by one halves the sensitivity.

.equ	ScaleStick	= 5


; This one determines the maximum Yaw command applied.
; Less gives less yaw autority, but also less possibility of motor saturation during full rudder stick.
; More gives more yaw autority, but also more possibility of motor saturation during full rudder stick.

.equ	YawLimit	= 250


; I-Term

#define I_TermGainYaw    0.2


; I-Term limits (1min, 512max)

.equ	I_TermLimitRoll  = 250
.equ	I_TermLimitPitch = 250
.equ	I_TermLimitYaw   = 250



; This one should be set to the chip you are using.
; Atmega48  = "m48def.inc"
; Atmega88  = "m88def.inc"
; Atmega168 = "m168def.inc"
; Atmega328 = "m328def.inc"

.include "m168def.inc"	

;*******************************************************
	


;---  16.8 bit signed registers ---

.equ	Temp			=0

.equ	RxInRoll		=1
.equ	RxInPitch		=2
.equ	RxInYaw			=3
.equ	RxInCollective		=4

.equ	RxChannel		=5

.equ	GyroZeroRoll		=6
.equ	GyroZeroPitch		=7
.equ	GyroZeroYaw		=8

.equ	MotorOut1		=9
.equ	MotorOut2		=10
.equ	MotorOut3		=11
.equ	MotorOut4		=12

.equ	GyroRoll		=13
.equ	GyroPitch		=14
.equ	GyroYaw			=15

.equ 	Command			=16

.equ	IntegralRoll		=17
.equ	IntegralPitch		=18
.equ	IntegralYaw		=19

.equ	CtrlCommand		=23
.equ	Gain			=24
.equ	Integral		=25
.equ	IntegralLimit		=26

.equ	GainPotP		=27
.equ	GainPotI		=28
.equ	GainInYaw		=29

.equ	StickCentre		=30

.equ	B16_RegAdd=0x0100	;base address for the math library register array

.equ	B16_WorkAdd=0x0200	;base address for the math library work area

					;r0 and r1 are used by the MUL instruction


.def	RxChannel2StartL	=r2	;These 14 global registers cannot be reused. They are modified by the ISR's
.def	RxChannel2StartH	=r3

.def	RxChannel3StartL	=r4
.def	RxChannel3StartH	=r5
	
.def	RxChannel4StartL	=r6
.def	RxChannel4StartH	=r7

.def	RxChannel1L		=r8
.def	RxChannel1H		=r9

.def	RxChannel2L		=r10
.def	RxChannel2H		=r11

.def	RxChannel3L		=r12
.def	RxChannel3H		=r13

.def	RxChannel4L		=r14
.def	RxChannel4H		=r15



.equ 	FlagGyroCalibrated	=b16_workadd+24

.equ	FlagGyrodriftcancelOn	=b16_workadd+25

.equ	RollGyroDirection	=b16_workadd+26
.equ	PitchGyroDirection	=b16_workadd+27
.equ	YawGyroDirection	=b16_workadd+28

.equ	CalibrationDelayCounter	=b16_workadd+29

.equ	PotReverser		=b16_workadd+30

.equ	FlagFcArmed		=b16_workadd+31
.equ	CounterFcArm		=b16_workadd+32
.equ	CounterFcDisarm		=b16_workadd+33

.equ	FlagCollectiveZero	=b16_workadd+34
.equ	FlagStickCentre		=b16_workadd+35

.def	t			=r16	;Main temporary register
					
.def	PWM1			=r17
.def	PWM2			=r18
.def	PWM3			=r19
.def	PWM4			=r20

.def	Counter			=r21

.def	TempL			=r22
.def	TempH			=r23



.def	RxChannel1StartL	=r27	;these three global registers (xh,yl,yh) cannot be reused. They are modified by the ISR's
.def	RxChannel1StartH	=r28

.def	tisp			=r29	;ISR temporary register

	


.macro led0_on			;macros for the LED's
	sbi portb,6
.endmacro
.macro led0_off
	cbi portb,6
.endmacro



#define pwm_out_pin_1 portb,2	;motor output pin assignments
#define pwm_out_pin_2 portb,1
#define pwm_out_pin_3 portb,0
#define pwm_out_pin_4 portd,7
#define pwm_out_pin_5 portd,6
#define pwm_out_pin_6 portd,5


.include "168mathlib_ram_macros_v1.asm"

.org 0


#if defined(__ATmega48__) || defined(__ATmega88__)
#message "rjmp"
	rjmp reset
	rjmp RxChannel2
	rjmp RxChannel3
	rjmp RxChannel4
	rjmp unused
	rjmp RxChannel1
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused
	rjmp unused

#elif defined(__ATmega168__) || defined(__ATmega328__)
#message "jmp"
	jmp reset
	jmp RxChannel2
	jmp RxChannel3
	jmp RxChannel4
	jmp unused
	jmp RxChannel1
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
	jmp unused
#else
#error "Unsupported part:" __PART_NAME__
#endif

unused:	reti

reset:	ldi t,low(ramend)	;initalize stack pointer
	out spl,t
	ldi t,high(ramend)
	out sph,t

;--- setup IO ---

	;       76543210
	ldi t,0b01111111
	out ddrb,t

	;       76543210
	ldi t,0b11000000
	out ddrc,t
	
	;       76543210
	ldi t,0b11110001
	out ddrd,t


;--- Setup pin change interrupt on PD1, PD2, PD3, PB4

	;       76543210
	ldi t,0b00000101	;PB7, PB1
	sts pcicr,t

	;       76543210	
	ldi t,0b10000000	;PB7
	sts pcmsk0,t
	
	;       76543210
	ldi t,0b00000010	;PD1
	sts pcmsk2,t


	;       76543210
	ldi t,0b00000101	;PD2, PD3
	sts eicra,t
	

	;       76543210
	ldi t,0b00000011	;PD2, PD3
	out eimsk,t		;STS? OUT?  Come on!



;---- Setup timer2 to run at 125kHz ----

	;       76543210
	ldi t,0b00000100
	sts tccr2b,t

;---- Setup timer1 to run at 1MHz ----

	;       76543210
	ldi t,0b00000010
	sts tccr1b,t

;---- Initalize variables ---

	clr t
	sts FlagGyroCalibrated,t	;FlagGyroCalibrated = false
	sts FlagFcArmed,t		;FlagFcArmed = false
	sts FlagCollectiveZero, t

	sts CounterFcArm,t
	sts CounterFcDisarm,t

	sts FlagStickCentre,t	;FlagStickCentering = false

	ser t
	sts CalibrationDelayCounter, t

	ldi zl,6							; get StickCentre saved value
	ldi zh,0			
	rcall ReadEeprom
	sts (StickCentre*4)+0x0100,t
	ldi zl,7
	ldi zh,0			
	rcall ReadEeprom
	sts (StickCentre*4)+0x0101,t
	ldi zl,8
	ldi zh,0			
	rcall ReadEeprom
	sts (StickCentre*4)+0x0102,t

	b16ldi Temp, 1400
	b16cmp StickCentre,Temp
	brge sc1
	b16ldi StickCentre,1500				; set to 1500 if less than 1400
		
sc1:	
	b16ldi Temp, 1600
	b16cmp StickCentre,Temp
	brlt sc2
	b16ldi StickCentre,1500				; set to 1500 if more than 1600

sc2:
			
	ldi Templ,low(1500)		;prime the channels
	ldi Temph,high(1500)

	mov RxChannel1L,Templ
	mov RxChannel1H,Temph
	mov RxChannel2L,Templ
	mov RxChannel2H,Temph
	mov RxChannel4L,Templ
	mov RxChannel4H,Temph
	ldi Templ,low(1150)
	ldi Temph,high(1150)
	mov RxChannel3L,Templ
	mov RxChannel3H,Temph



;----
	sei

	led0_on				;Flash LED once, I am alive!

	ldi zl,250
	rcall wms

	led0_off

	ldi zh,10			;2 second delay
ca6:	ldi zl,200
	rcall wms
	dec zh
	brne ca6
	

;---- Gyro direction reversing ----
;---- 1: Set roll gain pot to zero.
;---- 2: Turn on flight controller.
;---- 3: LED flashes 3 times.
;---- 4: Move the stick for the gyro you want to reverse.
;---- 5: LED will blink continually.
;---- 6: Turn off flight controller.
;---- 7: If there is more gyros to be reversed, goto step 2, else set roll gain pot back. 

	rcall ReadGainPots
	rcall ReadGainPots

	b16ldi Temp, 3.2
	b16cmp GainPotP,Temp
	brlt ca9			;enter gyro direction reversing?
		rjmp ca1 

ca9:	
	b16cmp	GainInYaw,Temp
	brge ca7			;enter StickCentre calibration?
	rjmp sc5

ca7:	ldi Counter,3			;start gyro reverse routiune, flash led 3 times, slowly
ca7a:	led0_on
	ldi zl,0
	rcall wms
	led0_off
	ldi zl,0
	rcall wms
	dec Counter
	brne ca7a


ca5:	ldi zl,18
	rcall wms

	rcall RxGetChannels

	b16ldi Temp, 307.2

	b16cmp RxInRoll,Temp		;Roll TX stick moved?
	ldi zl,0
	brge ca8

	b16cmp RxInPitch,Temp		;Pitch TX stick moved?
	ldi zl,1
	brge ca8

	b16cmp RxInYaw,Temp		;Yaw TX stick moved?
	ldi zl,2
	brge ca8

	b16cmp RxInCollective,Temp	;Throttle TX stick moved?
	ldi zl,3
	brge ca8

	rjmp ca5			;no

ca8:	ldi zh,0			;yes, reverse direction
	rcall ReadEeprom

	ldi Templ,0x80
	eor t,Templ					

	rcall WriteEeprom		;Store gyro direction to EEPROM

ca4:	led0_on				;flash LED fast
	ldi zl,100
	rcall wms
	led0_off
	ldi zl,100
	rcall wms
	rjmp ca4



;---- ESC Throttle range calibration. This outputs collective input to all motor outputs ---
;---- This mode is entered by turning yaw gain pot to zero and turning on the flight controller. ---
	
ca1:	b16cmp GainInYaw,Temp
	brlt ca2a			;enter ESC throttle range calibration?
	rjmp ma1

ca2a:	ldi Counter,2			;start esc throttle range cal, flash led 2 times then stays on all the time
ca2:	led0_on
	ldi zl,0
	rcall wms
	led0_off
	ldi zl,0
	rcall wms
	dec Counter
	brne ca2

	led0_on					;now leave LED on so you know it's armed

ca3:	ldi zl,18
	rcall wms

	rcall RxGetChannels

	b16mov MotorOut1,RxInCollective		;output collective to all ESC's
	b16mov MotorOut2,RxInCollective
	b16mov MotorOut3,RxInCollective
	b16mov MotorOut4,RxInCollective
	rcall PWMGenStart			;output ESC signal
	rcall PWMGenEnd
	rjmp ca3 				;do until the cows come home.

;---- StickCentre - learns centre of Yaw on TX -----
;---- Set yaw and roll gain pot to zero, turn on FC, wait for quick LED flashes, turn off FC and reset pots. ----
;---- Led flashes slow 10 times than fast when finished -----

sc5:
    ldi Counter,10			;start stick centre calibration, flash led slow 10 times
ca13:	led0_on
	ldi zl,0
	rcall wms
	led0_off
	ldi zl,0
	rcall wms
	dec Counter
	brne ca13

	ser	t
	sts FlagStickCentre,t			; stops scaling to -/+512
	rcall RxGetChannels

	b16mov StickCentre,RxInYaw		; save unmodified value (should be close to 1500)

	lds t,(StickCentre*4)+0x0100
	ldi	zl,6
	rcall WriteEeprom
	lds t,(StickCentre*4)+0x0101
	ldi	zl,7
	rcall WriteEeprom
	lds t,(StickCentre*4)+0x0102
	ldi	zl,8
	rcall WriteEeprom
	
	rjmp ca4



	;--- Main loop ---

ma1:	rcall GetGyroDirections

	rcall RxGetChannels


	;--- Arming/Disarming ---


	lds t,FlagCollectiveZero
	tst t
	brne ma80x
	rjmp ma80

ma80x:	b16ldi Temp, -256		;Disarm?
	b16cmp RxInYaw,Temp
	brge ma81

	lds t,CounterFcDisarm
	inc t
	sts CounterFcDisarm,t
	cpi t, 100
	breq ma82x
	rjmp ma82

ma82x:	clr t
	sts FlagFcArmed,t
	rjmp ma80


ma81:	b16neg Temp			;Arm?
	b16cmp RxInYaw,Temp
	brlt ma80

	lds t,CounterFcArm
	inc t
	sts CounterFcArm,t
	cpi t, 100
	brne ma82

	ser t
	sts FlagFcArmed,t

ma80:	clr t
	sts CounterFcDisarm, t
	sts CounterFcArm,t

ma82:	


	;--- Calibrate gyro when collective below 1% ---

	lds t,FlagCollectiveZero
	tst t
	brne ma12					;collective below 1% ?
	rjmp ma4

ma12:	lds t,CalibrationDelayCounter			;yes, increase delay counter
	inc t
	breq ma50					;delay reached 256?
	
	sts CalibrationDelayCounter,t			;no, skip calibration.
	rjmp ma51

ma50:	b16clr GyroZeroRoll				;yes, set gyro zero value (average of 16 readings)
	b16clr GyroZeroPitch
	b16clr GyroZeroYaw

	ldi Counter,16

ma20:	rcall ReadGyros

	b16add GyroZeroRoll, GyroRoll
	b16add GyroZeroPitch, GyroPitch
	b16add GyroZeroYaw, GyroYaw

	dec Counter
	brne ma20

	b16fdiv GyroZeroRoll, 4				;divide by 16

	b16fdiv GyroZeroPitch, 4			;divide by 16

	b16fdiv GyroZeroYaw, 4				;divide by 16

	ser t
	sts FlagGyroCalibrated,t			;FlagGyroCalibrated = true
	
	sts CalibrationDelayCounter,t	

	rcall ReadGainPots

	b16clr IntegralRoll
	b16clr IntegralPitch
	b16clr IntegralYaw

	rjmp ma51
			
ma4:	clr t						;no, skip calibration, reset calibration delay
	sts CalibrationDelayCounter,t

ma51:



	rcall ReadGyros
	b16sub GyroRoll, GyroZeroRoll	;remove offset from gyro output
	b16sub GyroPitch, GyroZeroPitch
	b16sub GyroYaw, GyroZeroYaw


	;--- Start mixing by setting collective to motor input 1,2,3 and 4 ---

	b16mov MotorOut1,RxInCollective
	b16mov MotorOut2,RxInCollective
	b16mov MotorOut3,RxInCollective
	b16mov MotorOut4,RxInCollective



	;--- Calculate roll command output ---
	
	lds t, RollGyroDirection
	tst t
	brpl ma60
	
	b16neg GyroRoll

ma60:	b16fdiv GyroRoll, ScaleGyro			;scale inputs
	b16fdiv RxInRoll, ScaleStick

	b16mov CtrlCommand, GyroRoll			;calculate error
	b16add CtrlCommand, RxInRoll

	b16add IntegralRoll, CtrlCommand 		;I

	b16mov Integral, IntegralRoll			;I-term limit
	b16ldi IntegralLimit, I_TermLimitRoll
	rcall ILimit  
	b16mov IntegralRoll, Integral

	b16mul CtrlCommand, GainPotP			;P gain

	b16mov Temp, IntegralRoll			;I gain
	b16mul Temp, GainPotI
	
	b16add CtrlCommand, Temp


	;--- Add roll command output to motor 1,2,3,4 ---

	b16add MotorOut1, CtrlCommand
	b16add MotorOut2, CtrlCommand
	b16sub MotorOut3, CtrlCommand
	b16sub MotorOut4, CtrlCommand



	;--- Calculate pitch command output ---

	lds t, PitchGyroDirection
	tst t
	brpl ma61
	
	b16neg GyroPitch

ma61:	b16fdiv GyroPitch, ScaleGyro			;scale inputs
	b16fdiv RxInPitch, ScaleStick

	b16mov CtrlCommand, GyroPitch			;calculate error
	b16add CtrlCommand, RxInPitch

	b16add IntegralPitch, CtrlCommand 		;I

	b16mov Integral, IntegralPitch			;I-term limit
	b16ldi IntegralLimit, I_TermLimitPitch
	rcall ILimit  
	b16mov IntegralPitch, Integral

	b16mul CtrlCommand, GainPotP			;P gain


	rcall PWMGenStart				;*** NOTE **** Adding more code between PWMGenStart and PWMGenEnd may result in a base pulse longer than 1ms and a copter in your face



	b16mov Temp, IntegralPitch			;I gain
	b16mul Temp, GainPotI

	b16add CtrlCommand, Temp

	
	;--- Add pitch command output to motor 1,2,3,4 ---

	b16add MotorOut1, CtrlCommand
	b16sub MotorOut2, CtrlCommand
	b16add MotorOut3, CtrlCommand
	b16sub MotorOut4, CtrlCommand


	
	;--- Calculate yaw command output ---

	lds t, YawGyroDirection
	tst t
	brpl ma62
	
	b16neg GyroYaw

ma62:	b16fdiv GyroYaw, ScaleGyro			;scale inputs
	b16fdiv RxInYaw, ScaleStick

	b16mov CtrlCommand, GyroYaw			;calculate error
	b16add CtrlCommand, RxInYaw

	b16add IntegralYaw, CtrlCommand 		;I

	b16mov Integral, IntegralYaw			;I-term limit
	b16ldi IntegralLimit, I_TermLimitYaw
	rcall ILimit  
	b16mov IntegralYaw, Integral

	b16mul CtrlCommand, GainInYaw			;P gain

	b16mov Temp, IntegralYaw			;I gain
	b16ldi Gain, I_TermGainYaw
	b16mul Temp, Gain	

	b16add CtrlCommand, Temp


	b16ldi Temp, YawLimit				;limit Yaw command to -YawLimit and YawLimit
	b16cmp CtrlCommand, Temp
	brlt ma90
	b16mov CtrlCommand, Temp

ma90:	b16neg Temp
	b16cmp CtrlCommand, Temp
	brge ma91
	b16mov CtrlCommand, Temp
ma91:


	;--- Add yaw command output to motor 1,2,3 and 4 ---

	b16sub MotorOut1, CtrlCommand
	b16add MotorOut2, CtrlCommand
	b16add MotorOut3, CtrlCommand
	b16sub MotorOut4, CtrlCommand



	;--- Limit the lowest value to avoid stopping of motor if motor value is under-saturated ---

	b16ldi Temp, 102.4   	;this is the motor idle level

	b16cmp MotorOut1, Temp
	brge ma40
	b16mov MotorOut1, Temp

ma40:	b16cmp MotorOut2, Temp
	brge ma41
	b16mov MotorOut2, Temp

ma41:	b16cmp MotorOut3, Temp
	brge ma42
	b16mov MotorOut3, Temp

ma42:	b16cmp MotorOut4, Temp
	brge ma43
	b16mov MotorOut4, Temp

ma43:


	;---- Update Status LED ----

	lds Templ, FlagGyroCalibrated		;LED on if (FlagGyroCalibrated && FlagFcArmed) true
	lds Temph, FlagFcArmed
	and Templ, Temph
	brne ma7
	led0_off
	rjmp ma8
ma7:	led0_on		
ma8:

	;--- Output to motor ESC's ---

	lds t,FlagCollectiveZero		;turn on motors if (FlagGyroCalibrated && FlagFcArmed && !FlagCollectiveZero) true
	com t
	and Templ,t
	brne ma6

	b16clr MotorOut1			;set motor 1-4 to zero
	b16clr MotorOut2
	b16clr MotorOut3
	b16clr MotorOut4

ma6:	rcall PWMGenEnd
	rjmp ma1


	;--- End of main loop ---


	;--- Subroutines ---




ILimit:	b16cmp Integral, IntegralLimit
	brlt il1
	b16mov Integral, IntegralLimit

il1:	b16neg IntegralLimit
	b16cmp Integral, IntegralLimit
	brge il2
	b16mov Integral, IntegralLimit

il2:	ret



	;--- Output motor ppm channels 1-4 in parallell ---
	; Input is 0 to 1023



PWMGenStart:
	sbi pwm_out_pin_1	;turn on pins
	sbi pwm_out_pin_2
	sbi pwm_out_pin_3
	sbi pwm_out_pin_4

;sbi pwm_out_pin_5
;sbi pwm_out_pin_6

	;       76543210
	ldi t,0b00000010	;reset prescaler2 and counter2
	out gtccr,t
	clr t
	sts tcnt2, t

	ret




PWMGenEnd:
	b16ldi Temp, 1023		;limit to 1023
	b16cmp MotorOut1,Temp
	brlt ou5
	b16mov MotorOut1,Temp
ou5:
	b16cmp MotorOut2,Temp
	brlt ou6
	b16mov MotorOut2,Temp
ou6:
	b16cmp MotorOut3,Temp
	brlt ou7
	b16mov MotorOut3,Temp
ou7:
	b16cmp MotorOut4,Temp
	brlt ou8
	b16mov MotorOut4,Temp
ou8:

	
	b16fdiv MotorOut1, 2
	b16fdiv MotorOut2, 2
	b16fdiv MotorOut3, 2
	b16fdiv MotorOut4, 2

	b16load MotorOut1
	lds PWM1,b168temp1

	b16load MotorOut2
	lds PWM2,b168temp1

	b16load MotorOut3
	lds PWM3,b168temp1

	b16load MotorOut4
	lds PWM4,b168temp1




;cbi pwm_out_pin_5

ou69:	lds t, tcnt2
	cpi t, 126
	brlo ou69

;cbi pwm_out_pin_6	

	


	ldi Counter,0

tu0:	subi PWM1, 1		;1         31 cycles total
	brcc ow1		;2  1
	cbi pwm_out_pin_1	;   2
	rjmp tu1		;   2
ow1:	nop			;1
	nop			;1
	nop			;1
tu1:		

	subi PWM2, 1		;1
	brcc ow2		;2  1
	cbi pwm_out_pin_2	;   2
	rjmp tu2		;   2
ow2:	nop			;1
	nop			;1
	nop			;1
tu2:		

	subi PWM3, 1		;1
	brcc ow3		;2  1
	cbi pwm_out_pin_3	;   2
	rjmp tu3		;   2
ow3:	nop			;1
	nop			;1
	nop			;1
tu3:		

	subi PWM4, 1		;1
	brcc ow4		;2  1
	cbi pwm_out_pin_4	;   2
	rjmp tu4		;   2
ow4:	nop			;1
	nop			;1
	nop			;1
tu4:		

	nop			;cycles for rent
	nop
	nop
	nop

	dec Counter		;1
	brne tu0		;2

	ret	





	;--- Read ADC's ---

ReadGyros:
	;       76543210
	ldi t,0b00111111
	sts didr0,t

	;       76543210
	ldi t,0b00000000
	sts adcsrb,t


	;       76543210	;read roll
	ldi t,0b00000010
	rcall read_adc
	b16store GyroRoll

	;       76543210	;read pitch
	ldi t,0b00000001
	rcall read_adc
	b16store GyroPitch

	;       76543210	;read yaw
	ldi t,0b00000000
	rcall read_adc
	b16store GyroYaw

	ret



ReadGainPots:

	ldi zl,3		;get PotReverser from EEPROM
	ldi zh,0
	rcall ReadEeprom
	sts PotReverser,t


	;       76543210
	ldi t,0b00111111
	sts didr0,t

	;       76543210
	ldi t,0b00000000
	sts adcsrb,t


	;       76543210	;read P gain
	ldi t,0b00000011
	rcall read_adc

	lds t,PotReverser
	tst t
	brmi ga1
	rcall invert
ga1:	b16store GainPotP
	b16fdiv GainPotP, 4

	;       76543210	;read I gain
	ldi t,0b00000100
	rcall read_adc

	lds t,PotReverser
	tst t
	brmi ga2
	rcall invert
ga2:	b16store GainPotI
	b16fdiv GainPotI, 11

	;       76543210	;read yaw P gain
	ldi t,0b00000101
	rcall read_adc

	lds t,PotReverser
	tst t
	brmi ga3
	rcall invert
ga3:	b16store GainInYaw
	b16fdiv GainInYaw, 4

	ret


invert:	lds Templ, b168Temp1
	lds Temph, b168Temp2
	ldi t,0xff
	eor Templ,t
	ldi t,0x03
	eor Temph,t
	sts b168Temp1, Templ
	sts b168Temp2, Temph
	ret


read_adc:			;x = adc    y = 0
	sts admux,t		;set ADC channel
	;       76543210
	ldi t,0b11000110	;start ADC
	sts adcsra,t

re1:	lds t,adcsra		;wait for ADC to complete
	sbrc t,6
	rjmp re1

	lds Templ,adcl		;read ADC
	lds Temph,adch
	
	clr t
	sts b168Temp0, t
	sts b168Temp1, Templ
	sts b168Temp2, Temph
	
	ret


wms:	ldi t,235	; wait zl ms
wm1:	ldi Templ,11	;34
wm2:	dec Templ
	brne wm2
	dec t
	brne wm1
	dec zl
	brne wms
	ret



	;--- Read RX channels 1-4, pin change interrupt driven ---
	;these have to be fast as possible to minimize jitter in loop timed events

RxChannel1:
	in tisp, sreg

	sbis pind,1				;rising or falling?
	rjmp rx1m1


	lds RxChannel1StartL, tcnt1l		;rising, store the start value
	lds RxChannel1StartH, tcnt1h

	out sreg,tisp				;exit	
	reti


rx1m1:	lds RxChannel1L, tcnt1l			;falling, calculate the pulse length
	lds RxChannel1H, tcnt1h

	sub RxChannel1L, RxChannel1StartL
	sbc RxChannel1H, RxChannel1StartH

	out sreg,tisp				;exit	
	reti


RxChannel2:
	in tisp, sreg

	sbis pind,2				;rising or falling?
	rjmp rx2m1


	lds RxChannel2StartL, tcnt1l		;rising, store the start value
	lds RxChannel2StartH, tcnt1h

	out sreg,tisp				;exit	
	reti


rx2m1:	lds RxChannel2L, tcnt1l			;falling, calculate the pulse length
	lds RxChannel2H, tcnt1h

	sub RxChannel2L, RxChannel2StartL
	sbc RxChannel2H, RxChannel2StartH

	out sreg,tisp				;exit	
	reti



RxChannel3:
	in tisp, sreg

	sbis pind,3				;rising or falling?
	rjmp rx3m1


	lds RxChannel3StartL, tcnt1l		;rising, store the start value
	lds RxChannel3StartH, tcnt1h

	out sreg,tisp				;exit	
	reti


rx3m1:	lds RxChannel3L, tcnt1l			;falling, calculate the pulse length
	lds RxChannel3H, tcnt1h

	sub RxChannel3L, RxChannel3StartL
	sbc RxChannel3H, RxChannel3StartH

	out sreg,tisp				;exit	
	reti



RxChannel4:
	in tisp, sreg

	sbis pinb,7				;rising or falling?
	rjmp rx4m1


	lds RxChannel4StartL, tcnt1l		;rising, store the start value
	lds RxChannel4StartH, tcnt1h

	out sreg,tisp				;exit	
	reti


rx4m1:	lds RxChannel4L, tcnt1l			;falling, calculate the pulse length
	lds RxChannel4H, tcnt1h

	sub RxChannel4L, RxChannel4StartL
	sbc RxChannel4H, RxChannel4StartH

	out sreg,tisp				;exit	
	reti




	
	;--- Get and scale RX channel inputs ---


RxGetChannels:				;channel 1 (Roll)
	cli	
	mov Templ,RxChannel1L
	mov Temph,RxChannel1H
	sei

	rcall XAbs

	clr t
	sts b168Temp0, t
	sts b168Temp1, Templ
	sts b168Temp2, Temph
	b16store RxChannel

	rcall ScaleMinus512To512

	b16mov RxInRoll,RxChannel



	cli				;channel 2 (Pitch)	
	mov Templ,RxChannel2L
	mov Temph,RxChannel2H
	sei

	rcall XAbs

	clr t
	sts b168Temp0, t
	sts b168Temp1, Templ
	sts b168Temp2, Temph
	b16store RxChannel

	rcall ScaleMinus512To512

	b16mov RxInPitch,RxChannel



	cli				;channel 3 (Collective)	
	mov Templ,RxChannel3L
	mov Temph,RxChannel3H
	sei

	rcall XAbs

	clr t
	sts b168Temp0, t
	sts b168Temp1, Templ
	sts b168Temp2, Temph
	b16store RxChannel

	rcall Scale0To1024

	b16mov RxInCollective,RxChannel



	cli				;channel 4 (Yaw)	
	mov Templ,RxChannel4L
	mov Temph,RxChannel4H
	sei

	rcall XAbs

	clr t
	sts b168Temp0, t
	sts b168Temp1, Templ
	sts b168Temp2, Temph
	b16store RxChannel

	rcall ScaleMinus512To512

	b16mov RxInYaw,RxChannel

	

	clr t				;Set FlagCollectiveZero true if collective is < 1
	sts FlagCollectiveZero,t

	b16ldi Temp, 1
	b16cmp RxInCollective,Temp
	brge c5

	ser t
	sts FlagCollectiveZero,t

c5:	ret



XAbs:	tst Temph		;X = ABS(X)
	brpl xa1

	ldi t,0xff
	eor Templ,t
	eor Temph,t
	
	ldi t,1
	add Templ,t
	clr t
	adc Temph,t

xa1:	ret


ScaleMinus512To512:
	lds t,FlagStickCentre
	tst t
	brne sc4				
	b16sub RxChannel, StickCentre
sc4:	ret


Scale0To1024:
	b16ldi Temp, 1150
	b16sub RxChannel, Temp
	brcc sc3
	b16clr RxChannel
sc3:	ret




ReadEeprom:
	out eearl,zl	;(Z) -> t
	out 0x22,zh

	ldi t,0x01
	out eecr,t

	in t, eedr
	ret


WriteEeprom:
	cli		;t -> (Z)

wr1:	sbic eecr,1
	rjmp wr1

	out eearl,zl
	out 0x22,zh

	out eedr,t

	;       76543210
	ldi t,0b00000100
	out eecr,t

	;       76543210
	ldi t,0b00000010
	out eecr,t

	sei

	ret


GetGyroDirections:
	clr zl				;Get roll gyro directions from EEPROM
	clr zh
	rcall ReadEeprom
	sts RollGyroDirection,t

	ldi zl,1			;Get pitch gyro directions from EEPROM
	clr zh
	rcall ReadEeprom
	sts PitchGyroDirection,t

	ldi zl,2			;Get yaw gyro directions from EEPROM
	clr zh
	rcall ReadEeprom
	sts YawGyroDirection,t

	ret





	;--- Debug: ----
/*
ShowGyros:
	ldi Templ,0x0d
	rcall SerbyteOut

	ldi Templ,0x0a
	rcall SerbyteOut


	b16load GyroRoll
	rcall B1616Out
	ldi Templ,' '
	rcall SerByteOut

	b16load GyroPitch
	rcall B1616Out
	ldi Templ,' '
	rcall SerByteOut

	b16load GyroYaw
	rcall B1616Out
	ldi Templ,' '
	rcall SerByteOut

	ret



ShowChannels:
	
	ldi Templ,0x0d
	rcall SerbyteOut

	ldi Templ,0x0a
	rcall SerbyteOut


	b16load RxInRoll
	rcall B1616Out
	ldi Templ,' '
	rcall SerByteOut

	b16load RxInPitch
	rcall B1616Out
	ldi Templ,' '
	rcall SerByteOut

	b16load RxInCollective
	rcall B1616Out
	ldi Templ,' '
	rcall SerByteOut

	b16load RxInYaw
	rcall B1616Out
	ldi Templ,' '
	rcall SerByteOut

	ret




B1616Out:
	lds t, b168Temp0 
	push t
	lds t, b168Temp1 
	push t
	lds Templ, b168Temp2 

	rcall SerByteAsciiOut
	pop Templ
	rcall SerByteAsciiOut
	ldi Templ,'.'
	rcall SerByteOut
	pop Templ
	rcall SerByteAsciiOut
	ret


B16Out:
	push Templ

	mov Templ,Temph
	rcall SerByteAsciiOut
	pop Templ
	rcall SerByteAsciiOut
	ret





b168Out:ldi Templ,0x0d
	rcall SerbyteOut

	ldi Templ,0x0a
	rcall SerbyteOut

B168cnt:b16load Temp

	lds t, b168Temp2	;negative?
	tst t
	brpl b168cn1

	ldi Templ,'-'		;yes
	rcall SerbyteOut

	lds t, b168Temp0	;negate b168Temp
	com t
	sts b168Temp0, t

	lds t, b168Temp1
	com t
	sts b168Temp1, t

	lds t, b168Temp2
	com t
	sts b168Temp2, t

	lds t, b168Temp0
	ldi Templ,1
	add t, Templ
	sts b168Temp0, t

	lds t, b168Temp1
	clr Templ
	adc t, Templ
	sts b168Temp1, t

	lds t, b168Temp2
	adc t, Templ
	sts b168Temp2, t

	rjmp b168cn2

b168cn1:ldi Templ,' '		;no
	rcall SerbyteOut

b168cn2:rcall B1616Out
	ldi Templ,' '
	rcall SerByteOut
	ret

	;--- Debug: Output byte Templ (ASCII) to serial port pin at 115200 8N1 ----

SerByteAsciiOut:


	push Templ
	swap Templ
	rcall su1		;high nibble
	pop Templ
	rjmp su1		;low nibble

su1:	andi Templ,0x0f
	ldi zl,low(su2*2)	;output one nibble in ASCII
	add zl,Templ
	ldi zh,high(su2*2)
	clr Templ
	adc zh,Templ
	lpm Templ,z
	rjmp SerByteOut

su2:	.db "0123456789ABCDEF"


		



	;--- Debug: Output byte Templ (binary) to serial port pin at 57600 8N1 ----

SerByteOut:
	cbi portd,4		;startbit
	nop
	nop
	nop

	rcall BaudRateDelay	

	ldi Temph,8		;databits

se3:	ror Templ

	brcc se1
	nop
	sbi portd,4
	rjmp se2
se1:	cbi portd,4
	nop
	nop

se2:	rcall BaudRateDelay

	dec Temph
	brne se3

	nop
	nop
	nop
	nop

	sbi portd,4			;stopbit
	nop 
	nop
	nop
	rcall BaudRateDelay

	ret

BaudRatedelay:

	ldi t,40		;this delay may need tweaking to give errorfree transfer
ba1:	dec t
	brne ba1
	ret
	


*/

.undef t

.include "168mathlib_ram_v1.asm"






