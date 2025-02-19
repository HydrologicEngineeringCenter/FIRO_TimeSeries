#####
##### STATE VARIABLE SCRIPT INITIALIZATION SECTION
#####

#####
##### STATE VARIABLE SCRIPT INITIALIZATION SECTION
#####

from hec.script import Constants
from hec import JdbcTimeSeriesDatabase
from hec.ensemble import EnsembleTimeSeries
from hec.ensemble import TimeSeriesIdentifier
from hec.ensemble import Ensemble
#
# initialization function. optional.
# set up tables and other things that only need to be performed once at the start of the compute.
#
# variables that are passed to this script during the compute initialization:
# 	currentVariable - the StateVariable that holds this script
# 	network - the ResSim network
#
# throw a hec.rss.lang.StopComputeException from anywhere in the script to
# have ResSim stop the compute.
# from hec.rss.lang import StopComputeException
# raise StopComputeException("the reason to stop the compute")
#
def initStateVariable(currentVariable, network):
	# return Constants.TRUE if the initialization is successful and Constants.FALSE if it failed.  
	# Returning Constants.FALSE will halt the compute.

	# get global variable
            
	externalGlobalVariable1=network.getGlobalVariable("ensemble")
	print(externalGlobalVariable1)              
    # get source/location from global variable
	dbFilename = externalGlobalVariable1.getSource()
	dbLocation = externalGlobalVariable1.getDataLocation()


	
	run = network.getRssRun()
	fileName = run.getDSSOutputFile()
	print("hi",fileName)
#	db = JdbcTimeSeriesDatabase("C:\FIRO_TSEnsembles/ResSim.db",JdbcTimeSeriesDatabase.CREATION_MODE.OPEN_EXISTING_UPDATE)
	db = JdbcTimeSeriesDatabase(dbFilename,JdbcTimeSeriesDatabase.CREATION_MODE.OPEN_EXISTING_UPDATE)
	tsid = TimeSeriesIdentifier(dbLocation,"flow")
	reader = db.getEnsembleTimeSeries(tsid)  
	currentVariable.varPut("reader", reader)
	issueDates = reader.getIssueDates()
	currentVariable.varPut("issueDates", issueDates)
	
	return Constants.TRUE

#####
##### STATE VARIABLE SCRIPT COMPUTATION SECTION
#####

#####
##### STATE VARIABLE SCRIPT COMPUTATION SECTION
#####

from java.time import  ZoneId
from java.time import  ZonedDateTime
from hec.rss.lang import StopComputeException
from hec.stats import MaxAvgDuration
import math

# This Script demonstrates using ensembles (FIRO_TSEnsembles.jar) within ResSim
# https://github.com/HydrologicEngineeringCenter/FIRO_TSEnsembles

# The ResSim environment needs FIRO_TSEnsembles.jar and sqlite-jdbc-3.30.1.jar to be in the class path

# modify Hec-ResSim.config append to the class path

# addjars ..\FIRO_TSEnsembles\build\libs


  
  
def getZonedDateTime(currentRuntimestep):
  jd = currentRuntimestep.getHecTime().getJavaDate(0) # zero timezone offset
  t = ZonedDateTime.ofInstant(jd.toInstant(),ZoneId.of("America/Los_Angeles"))
  return  t


# computes index to specified exceedance (weibul rank)
# data              -- array of values to sort 
# percentExceedance -- percent exceedance to lookup (i.e.  10,50, 90)
#  returns index to input array that matches or exceeds the percent exceedance
def indexToExceedanceLevel(data,percentExceedance):
    
  #https://stackoverflow.com/questions/7851077/how-to-return-index-of-a-sorted-list
  index = sorted(range(len(data)), key=lambda k: data[k],  reverse=True)
  data.sort(reverse=True) # sort high to low
  size=len(data)
#  for i in range (size):
#    print i," index=",index[i]," ",data[i], float(i+1)/(float(size)+1)

  pos =int( math.ceil((size+1.0)*float(percentExceedance)/100.0-1.0))
  print "index = ",index[pos]
  return index[pos]


# compute volume for the first steps
# for each ensemble member
def computeVolumes(ensemble, steps):
 
  values = ensemble.getValues()
  numMembers =len(values) 

  numSteps = min(steps,len(values[0]))
 
  rval = [0]* numMembers # initilize to zero
  
  for member in range(numMembers):
    for i in range(numSteps):
      rval[member] += values[member][i]

  return rval


    
  
# no return values are used by the compute from this script.
#
# variables that are available to this script during the compute:
# 	currentVariable - the StateVariable that holds this script
# 	currentRuntimestep - the current RunTime step 
# 	network - the ResSim network

# The following represents an undefined value in a time series
# 	Constants.UNDEFINED

# to set the StateVariable's value use:
# 	currentVariable.setValue(currentRuntimestep, newValue)
# where newValue is the value you want to set it to.


def computeStats(t):
  reader = currentVariable.varGet("reader")
  timeOfNextEnsemble = currentVariable.varGet("timeOfNextEnsemble")
  e = reader.getEnsemble(timeOfNextEnsemble) 
  maxAD = MaxAvgDuration(2)
  output = e.iterateForTracesAcrossTime(maxAD);
  return output
            

# computeReleaseInfo reads an ensemble to determine reservoir release criteria
#
def computeReleaseInfo(t):
  reader = currentVariable.varGet("reader")
  timeOfNextEnsemble = currentVariable.varGet("timeOfNextEnsemble")
#  print("timeOfNextEnsemble "+timeOfNextEnsemble.toString())
  e = reader.getEnsemble(timeOfNextEnsemble) 

  if e is None:
    raise StopComputeException("Error: forecast not found for date: "+ t.toString() )
    
  #print "ensemble loaded issueDate = ",e.getIssueDate()
  issueDate = e.getIssueDate()
  issueDates = currentVariable.varGet("issueDates")
  idx = issueDates.indexOf(issueDate)
  if( idx+1  < issueDates.size()):
    timeOfNextEnsemble = issueDates.get(idx+1)
  else:
    print("no more ensembles...")
  
  currentVariable.varPut("timeOfNextEnsemble",timeOfNextEnsemble)
  data = computeVolumes(e,7*24) # compute 7 day volume from hourly data, for each ensemble member
  index = indexToExceedanceLevel(data,50)  # interpolate/lookup 50% exceedance index to ensemble
  e50percent = e.getValues()[index]
  print "len(e50percent) = ",len(e50percent)
  return t.getDayOfMonth() %2

# find an ensemble forecast that can be used with the model time stamp t
def getDateTimeForFirstEnsemble(t):
  issueDates = currentVariable.varGet("issueDates")
  timeOfNextEnsemble = issueDates.get(0)
  for z in issueDates:
    if z.compareTo(t) >0:
      break
    timeOfNextEnsemble = z

  return timeOfNextEnsemble

            

def ensembleProcessing():

  t = getZonedDateTime(currentRuntimestep)
 
  isFirst = not currentVariable.varExists("timeOfNextEnsemble")
  
  if isFirst:
	timeOfNextEnsemble = getDateTimeForFirstEnsemble(t)
	
  else:
    timeOfNextEnsemble = currentVariable.varGet("timeOfNextEnsemble")

  currentVariable.varPut("timeOfNextEnsemble",timeOfNextEnsemble)
  #print("t="+t.toString())
  #print("timeOfNextEnsemble = "+timeOfNextEnsemble.toString())
  if isFirst or t.isEqual(timeOfNextEnsemble) or t.isAfter(timeOfNextEnsemble) :
  #if isFirst or tnextEnsembleTimestamp.isAfter(t) :
    R = computeReleaseInfo(t)
    currentVariable.varPut("ReleaseInfo",R)
    print "updated R=",R
    print t
  else:
    R = currentVariable.varGet("ReleaseInfo")
	
  if R is None:
   raise StopComputeException("Error: missing ensemble forecast")

  return R
tempValue = ensembleProcessing()
#tempValue = 1 
currentVariable.setValue(currentRuntimestep, tempValue)

#####
##### STATE VARIABLE SCRIPT CLEANUP SECTION
#####

#####
##### STATE VARIABLE SCRIPT CLEANUP SECTION
#####

from hec.script import Constants
#
# script to be run only once, at the end of the compute. optional.

# variables that are available to this script during the compute:
# 	currentVariable - the StateVariable that holds this script
# 	network - the ResSim network

# The following represents an undefined value in a time series:
# 	Constants.UNDEFINED
#
# throw a hec.rss.lang.StopComputeException from anywhere in the script to
# have ResSim stop the compute.
# from hec.rss.lang import StopComputeException
# raise StopComputeException("the reason to stop the compute")

# add your code here...