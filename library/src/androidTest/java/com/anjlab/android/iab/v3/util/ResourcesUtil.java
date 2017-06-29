package com.anjlab.android.iab.v3.util;

import java.util.Scanner;

public class ResourcesUtil
{
    public static String loadFile(String path)
    {
        String result = "";
        Scanner sc = new Scanner(ResourcesUtil.class.getClassLoader().getResourceAsStream(path));
        while (sc.hasNextLine())
        {
            result += sc.nextLine();
        }
        sc.close();
        return result;
    }
}
