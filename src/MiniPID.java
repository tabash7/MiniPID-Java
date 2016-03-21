 
//Minipid


/**
* Tiny, easy to use PID implimentation with advanced usage capability
* Minimal usage:<br>
* setPID(p,i,d); <br>
* ...code... <br>
* output=getOutput(target); //call repeatedly
*/
class MiniPID{
  private double P=0;
  private double I=0;
  private double D=0;
  private double F=0;
  
  private double maxIOutput=0;
  private double maxError=0;
  private double errorSum=0;
  
  
  private double maxOutput=0; 
  private double minOutput=0;
  
  private double setpoint=0;
  
  private double lastActual=0;
   
  //**********************************
  //Configuration functions
  //**********************************
  MiniPID(double p, double i, double d){
	  P=p; I=i; D=d;
	  }
  MiniPID(double p, double i, double d, double f){
	  P=p; I=i; D=d; F=f;
	  }
  
  //**********************************
  //Configuration functions
  //**********************************
  public void setP(double p){P=p;}
  
  /**
   * Changes the I parameter 
   * Scales the accumulated error to avoid output errors. <br>
   * Eg, doubling the I term cuts the accumulated error in half, which results in the 
   * output change due to the I term constant during the transition
   * @param i New gain value for the I term
   */
  public void setI(double i){
	  if(I!=0){
		  errorSum=errorSum*I/i;
		  }
	  if(maxIOutput!=0){
		  maxError=maxIOutput/i;
	  }
	  I=i;
  } 
  
  public void setD(double d){D=d;}
  public void setF(double f){F=f;}
  public void setPID(double p, double i, double d){setP(p);setI(i);setD(D);}
  public void setPID(double p, double i, double d,double f){setP(p);setI(i);setD(d);setF(f);}
  
  /**
   * Set the maximum output value contributed by the I component of the system
   * This can be used to prevent large windup issues and make tuning simpler
   * @param maximum. Units are the same as the expected output value
   */
  public void setMaxIOutput(double maximum){
	  /* Internally maxError and Izone are similar, but scaled for different purposes. 
	   * The maxError is generated for simplifying math, since calculations against 
	   * the max error are far more common than changing the I term or Izone. 
	   */
	  maxIOutput=maximum;
	  if(I!=0){
		  maxError=maxIOutput/I;
	  }
	  }
  public void setMaxOutput(double output){ setMaxOutput(-output,output);}
  public void setMaxOutput(double minimum,double maximum){
	  if(maximum<minimum)return;
	  maxOutput=maximum;
	  minOutput=minimum;
  }
  
  //**********************************
  //Primary operating functions
  //**********************************
  public void setSetpoint(double setpoint){
	  this.setpoint=setpoint;
  } 
  
  /** Calculate the PID value needed to hit the target setpoint. 
  * Automatically re-calculates the output at each call. 
  * @param actual The monitored value
  * @param target The target value
  * @return calculated output value for driving the actual to the target 
  */
  public double getOutput(double actual, double setpoint){
    setSetpoint(setpoint);

    double output=0;
    double Poutput;
    double Ioutput;
    double Doutput;
    double Foutput;
    
    //Do the simple parts of the calculations
    double error=setpoint-actual;

    //Calculate P term
    Poutput=P*error;   
    
    //Calculate D Term
    //Note, this is negative. This actually "slows" the system if it's doing
    //the correct thing, and small values helps prevent output spikes and overshoot 
    Doutput= -D*(actual-lastActual);
    lastActual=actual;
    
    //Calculate F output. Notice, this depends only on the setpoint, and not the error. 
    Foutput=F*setpoint;
    
    //These three are easy, and can be added without any hassle
    output= Foutput + Poutput + Doutput;
    
    
    //The Iterm is more complex. There's several things to factor in to make it easier to deal with.
    // 1. maxIoutput restricts the amount of output contributed by the Iterm.
    // 2. prevent windup by not increasing errorSum if we're already running against our max Ioutput
    // 3. prevent windup by not increasing errorSum if output is output=maxOutput    
   Ioutput=I*errorSum;
    if(maxIOutput!=0){
    	Ioutput=constrain(Ioutput,-maxIOutput,maxIOutput); 
    }    

    //OK! We've constrained the outputs, so add to the output value
    output += Ioutput;
    
     
    //Figure out what we're doing with the error.
    if( minOutput!=maxOutput && !bounded(output, minOutput,maxOutput) ){
    	errorSum=error; 
    	// reset the error sum to a sane level
    	// Setting to current error ensures a smooth transition when the P term 
    	// decreases enough for the I term to start acting upon the controller
    	// From that point the I term will build up as would be expected
    }
    else if(maxIOutput!=0){
		errorSum=constrain(errorSum+error,-maxError,maxError);
		// In addition to output limiting directly, we also want to prevent I term 
		// buildup, so restrict the error directly
	}
	else{
		errorSum+=error;
	}
    
    //Restrict output to our specified limits
    if(minOutput!=maxOutput){ 
    	output=constrain(output, minOutput,maxOutput);
    	}
    
    //Get a test printline 
    //System.out.printf("Final output %5.2f [ %5.2f, %5.2f , %5.2f  ], eSum %.2f\n",output,P*error, Ioutput, -D*(setpoint-prevSetpoint),errorSum );
    //System.out.printf("%5.2f\t%5.2f\t%5.2f\t%5.2f\n",output,Poutput, Ioutput, Doutput );

    return output;
  	}
  
  	/**
  	 * Calculates the PID value using the last provided setpoint and actual valuess
  	 * @return calculated output value for driving the actual to the target 
  	 */
	public double getOutput(){
  		return getOutput(lastActual,setpoint);
  	}
	/**
	 * 
	 * @param actual
  	 * @return calculated output value for driving the actual to the target 
	 */
  	public double getOutput(double actual){
  		return getOutput(actual,setpoint);
  	}
  	  
  	//**************************************
  	// Helper functions
  	//**************************************

    /**
     * Forces a value into a specific range
     * @param value input value
     * @param min maximum returned value
     * @param max minimum value in range
     * @return Value if it's within provided range, min or max otherwise 
     */
    private double constrain(double value, double min, double max){
      if(value > max){ return max;}
      if(value < min){ return min;}
      return value;
    }  
    
    
    /**
     * Test if the value is within the min and max, inclusive
     * @param value to test
     * @param min Minimum value of range
     * @param max Maximum value of range
     * @return
     */
    private boolean bounded(double value, double min, double max){
        return (value < max) && (value >min);
    }
    
}