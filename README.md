# JRubyConcurrentConstantMemoryExcel

[![Build Status](https://travis-ci.org/Colisweb/JRubyConcurrentConstantMemoryExcel.svg?branch=master)](https://travis-ci.org/Colisweb/JRubyConcurrentConstantMemoryExcel)
[![codecov](https://codecov.io/gh/Colisweb/JRubyConcurrentConstantMemoryExcel/branch/master/graph/badge.svg)](https://codecov.io/gh/Colisweb/JRubyConcurrentConstantMemoryExcel)

Goal
----

This librairy helps you to write Excel files (`xlsx`) as fast as possible without, hopefully, blowing your heap.

It's meant to be used in JRuby programs.    
It can be used in Scala programs, of course, but there're better, pure, ways with such language to achieve the same goal.

It's a very opinionated librairy which does not provides you all the possible customizations.    
For example, it'll use all your CPU cores to compute the rows, it's not configurable and will maybe never be.

Be sure to read and understand the [heap usage considerations](#heap-usage-considerations) and [CPU usage considerations](#cpu-usage-considerations)
chapters of this README before using this lib in a production environment.

Installation
------------

In your `Jarfile`:

```ruby
source 'https://dl.bintray.com/colisweb/maven'

jar 'com.colisweb:JRubyConcurrentConstantMemoryExcel_2.12', '1.0.3'
```

Usage
-----

```ruby
require 'java'

java_import 'com.colisweb.jruby.concurrent.constant.memory.excel.ConcurrentConstantMemoryExcel'
java_import 'com.colisweb.jruby.concurrent.constant.memory.excel.Cell'

header     = ["A", "B", "C"].to_java(:string)
sheet_name = "cars"

# Curently, support only one sheet per workbook.
# 
workbook_state = ConcurrentConstantMemoryExcel.newWorkbookState(sheet_name, header)

to_parametrize_compute_rows_lambda = 
  ->(query) {
    ->() {
      rows = query.execute # example of an expensive query that fetches the rows data
        
      # Example of conversion of data to Cell.
      #
      # For now, only blank, string and numeric cells are supported.   
      # 
      rows.map { |row|
        row.map { |cell_data|
          if cell_data.nil?
            ConcurrentConstantMemoryExcel.blankCell
          elsif cell_data.is_a?(String)
            ConcurrentConstantMemoryExcel.stringCell(cell_data)
          elsif cell_data.is_a?(Fixnum)
            ConcurrentConstantMemoryExcel.numericCell(cell_data.to_f)
          else
            raise "For now, unsupported Cell type"
          end
        }
      }
    }
  }

# Here, we assume that the `queries` are sorted in the order we want the data to be ordered in the final xlsx file.
# 
# You can control in which order the data is written thanks to the last argument of the `ConcurrentConstantMemoryExcel.addRows` function.
# 
queries.each_with_index { |query, index|

  compute_rows_lambda = to_parametrize_compute_rows_lambda.call(query)

  # Do not launch any computation. It just registers required computations in the `workbook_state`.
  #
  # The second argument for this function call should be a lambda taking no parameter.   
  # 
  ConcurrentConstantMemoryExcel.addRows(workbook_state, compute_rows_lambda, index.to_java(:int))
  
}

# Computations of rows will really begin with this function call. Not before.
# 
ConcurrentConstantMemoryExcel.writeFile(workbook_state, "path/to/my/file") # will write a file named `file.xlsx` in the `path/to/my` directory.
```

Heap usage considerations
-------------------------

This librairy parallelizes the computations of your rows using `n` threads, where `n` is the number of cores your CPU has.

For each call to the `addRows` function, a computation is registered in the `workbook_state`.

When the `writeFile` function is called, all the registered computations will be launched, `n` by `n`.

So the maximum quantity of RAM this lib can use is equal to `n` times the quantity of RAM required to compute the `compute_rows_lambda`.

If your program OOM, the only way to fix that is by reducing the size of the result the `query` passed to the `parametrized_compute_rows_lambda` gives you when executed.

CPU usage considerations
-------------------------

Because this lib knows nothing about the computations you'll ask it to execute, in order to maximise the CPU usage, 
and so the speed of your Excel extraction, you'll have to ensure that the number of registered computations (number of call to the `addRows` function)
is superior or equal to the number of cores your CPU has.

If it's inferior to that number, maybe you can write your `queries` in a different way.

For example, instead of making 4 `query` each computing `1000` rows, you can write 8 `query` computing `500` rows.   
On a 8 cores machines, the result can be computed up to 2 times faster in this case.

Coding Style
------------

This project is a Scala librairy meant to be used in JRuby projects.

*That explains the quite impure style used in the Scala code.*

Acknowledgments
---------------

We want to thanks:

 - Nicolas Rinaudo [@NicolasRinaudo](https://twitter.com/NicolasRinaudo)
 - Elijah Rippeth [@terrible_coder](https://twitter.com/terrible_coder)
 - Mathieu Besançon [@matbesancon](https://twitter.com/matbesancon)
 - Charles Oliver Nutter [@headius](https://twitter.com/headius)

for their help in the writting of this lib.

🙂


 