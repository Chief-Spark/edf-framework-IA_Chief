#! /bin/bash

export jarName=$1
export envConfDir=$2
export validationApplicationDir=$3
export sparkConfDir=$4
# Go to run dir
# cd runSynthesis

# Run jar with input hc-format file
echo "Running ./target/$jarName com.experian.edf.oxygen.validation.app.SparkValidationApp $envConfDir $validationApplicationDir $sparkConfDir"
java -cp ../target/$jarName com.experian.edf.oxygen.validation.app.SparkValidationApp $envConfDir $validationApplicationDir $sparkConfDir

echo
echo
echo "Execution is done. Check your workspace configuration."
echo
