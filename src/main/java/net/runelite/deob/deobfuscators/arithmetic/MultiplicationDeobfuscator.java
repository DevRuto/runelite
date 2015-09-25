package net.runelite.deob.deobfuscators.arithmetic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.deob.ClassGroup;
import net.runelite.deob.Deobfuscator;
import net.runelite.deob.attributes.code.Instruction;
import net.runelite.deob.attributes.code.Instructions;
import net.runelite.deob.attributes.code.instruction.types.PushConstantInstruction;
import net.runelite.deob.attributes.code.instructions.IMul;
import net.runelite.deob.attributes.code.instructions.SiPush;
import net.runelite.deob.execution.Execution;
import net.runelite.deob.execution.Frame;
import net.runelite.deob.execution.InstructionContext;
import net.runelite.deob.execution.StackContext;

public class MultiplicationDeobfuscator implements Deobfuscator
{
	private ClassGroup group;
	
	// find a chain of multiplication instructions, evaluate and set one to the constant and the others to 1
	
	@Override
	public void run(ClassGroup group)
	{
		this.group = group;
		
		int i;
		while ((i = runOnce()) > 0)
			System.out.println("Replaced " + i + " constants");
	}
	
	private List<InstructionContext> getConstants(InstructionContext ctx)
	{
		List<InstructionContext> l = new ArrayList<>();
		
		assert ctx.getInstruction() instanceof IMul;
		
		for (StackContext sctx : ctx.getPops())
		{
			InstructionContext i = sctx.getPushed();
			
			if (i.getInstruction() instanceof IMul)
			{
				l.addAll(getConstants(i));
			}
			else if (i.getInstruction() instanceof PushConstantInstruction)
			{
				PushConstantInstruction pci = (PushConstantInstruction) i.getInstruction();
				int value = (int) pci.getConstant().getObject();
				if (value != 1) // already been touched, otherwise we keep multiplying the same ins over and over
					l.add(i);
			}
		}
		
		return l;
	}
	
	private boolean isOnlyPath(Execution execution, Frame frame, InstructionContext ctx)
	{
		for (Frame f : execution.processedFrames)
			if (f.getMethod() == frame.getMethod())
				for (InstructionContext i : f.getInstructions())
					if (i.getInstruction() == ctx.getInstruction())
					{
						if (!i.equals(ctx))
						{
							return false;
						}
					}
		return true;
	}
	
	private int runOnce()
	{
		group.buildClassGraph();
		
		Execution e = new Execution(group);
		e.populateInitialMethods();
		e.run();
		
		Set<Instruction> done = new HashSet<>();
		int count = 0;
		
		for (Frame frame : e.processedFrames)
			outer:
			for (InstructionContext ictx : frame.getInstructions())
			{
				Instruction instruction = ictx.getInstruction();
				Instructions instructions = instruction.getInstructions();
				
				if (!(instruction instanceof IMul))
					continue;
				
				List<InstructionContext> ins = getConstants(ictx);
				
				if (ins.size() == 1)
					continue;
				
				for (InstructionContext i : ins)
				{
					if (done.contains(i.getInstruction()))
					{
						continue outer;
					}
				}
				
				// there can only be one path to here, or else combinging would change code logic
				if (!isOnlyPath(e, frame, ictx))
					continue;
				
				int result = 1;
				
				// calculate result
				for (InstructionContext i : ins)
				{
					PushConstantInstruction pci = (PushConstantInstruction) i.getInstruction();
					int value = (int) pci.getConstant().getObject();
					
					result *= value;
				}
				
				// set result on ins
				for (InstructionContext i : ins)
				{
					PushConstantInstruction pci = (PushConstantInstruction) i.getInstruction();
					Instruction newIns = pci.setConstant(new net.runelite.deob.pool.Integer(result));
					++count;
					if (newIns != pci)
					{
						instructions.replace((Instruction) pci, newIns);
					}
					result = 1; // rest of the results go to 1
				}
				
				for (InstructionContext i : ins)
					done.add(i.getInstruction());
			}
		
		return count;
	}

}
