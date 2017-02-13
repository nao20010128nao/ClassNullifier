package com.nao20010128nao.ClassNullifier

import java.io.*
import java.util.regex.*
import joptsimple.*
import java.util.zip.*
import javassist.*
import java.util.*

OptionParser parser=new OptionParser()
parser.accepts("input").withRequiredArg()
parser.accepts("output").withOptionalArg()
def result=parser.parse(args)
File input,output
if (!result.has("input")) {
	System.exit 1
	return
} else {
	input = new File(result.valueOf("input").toString())
}
if (!result.has("output")) {
	def renameFilename={String s->
		def filename=s.split(Pattern.quote(File.separator)).last()
		def nonExtFn=filename.substring(0,filename.lastIndexOf("."))
		def ext=filename.substring(filename.lastIndexOf(".")+1)
		nonExtFn+="_nullified"
		return nonExtFn+"."+ext
	}
	output = new File(input.parentFile, renameFilename(input.absolutePath))
} else {
	output = new File(result.valueOf("output").toString()).absoluteFile
}

ClassPool cp=new ClassPool(false)
cp.appendClassPath(input.absolutePath)
Set<String> files=new HashSet<>()
input.withDataInputStream {dis->
	ZipInputStream zis=null
	try{
		zis=new ZipInputStream(dis)
		ZipEntry ze=null
		while ((ze = zis.nextEntry) != null) {
			files+=ze.name
		}
	}finally{
		if(zis!=null)
			zis.close()
	}
}
output.withDataOutputStream {dos->
	ZipOutputStream zos=null
	try{
		zos=new ZipOutputStream(dos)
		files.each{name->
			try {
				if (!name.endsWith(".class")) {
					println "Skipped: $name"
					return
				}
				String classNameGoes=name.substring(0,name.length()-6).replace("/", ".")
				println "Modifing: $classNameGoes"
				CtClass clazz=cp.get(classNameGoes)
				if (clazz.frozen) {
					println "Frozen: $classNameGoes"
					clazz.defrost()
				}
				clazz.stopPruning(true)
				clazz.methods.each{method->
					println method.name
					if ((method.modifiers & Modifier.ABSTRACT) != 0) {
						return
					}
					CtClass ret=method.returnType
					String bodyShouldBe="return null;"
					if (ret == CtClass.voidType) {
						bodyShouldBe = ";"
					} else if (ret.primitive) {
						bodyShouldBe = "return 0;"
					}
					method.body=bodyShouldBe
				}
				clazz.constructors.each{cnst->
					println "<init>"
					cnst.body=";"
				}
				if(clazz.classInitializer!=null)
					clazz.classInitializer.body=";"
				ZipEntry newZe=new ZipEntry(name)
				zos.putNextEntry(newZe)
				zos.write(clazz.toBytecode())
				clazz.defrost()
			} catch (Throwable e) {
				e.printStackTrace()
			}
		}
	}finally{
		if(zos!=null)
			zos.close()
	}
}